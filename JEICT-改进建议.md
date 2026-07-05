# JEICT 配方树模组改进建议

> 基于 1.20.1 Forge 源码（`com.lhy.jeict`）通读后的分析整理。按"影响面 × 实现成本"排序，前几项为优先建议。

---

## 一、架构与正确性

### 1. RecipeGraphCache 全局单例的失效与线程安全问题
`RecipeGraphCache.current` 是一个 `static` 字段，只在 `onRuntimeUnavailable` 时 `clear()`。

**问题**
- 玩家切换世界 / 服务器时不会清除：`onRuntimeAvailable` 之外，没有任何地方重新构建。换世界后 `RegistryAccess` 已经变了，缓存里仍是旧世界的 `RecipeNode.recipe` 引用，可能出现配方查询错位。
- `get(Minecraft)` 没做线程检查：理论上只在主线程调用，但 JEI 的 recipe transfer 有可能在异步路径触发 `transferRecipeFromJei`，`createNode` 又会回链路 `Optional.ofNullable(current)`。

**建议**
- 在 `ClientPlayerNetworkEvent.LoggingOut` / `LevelEvent.Unload` 时调用 `RecipeGraphCache.clear()`。
- `get()` 里加 `if (!minecraft.isSameThread()) return Optional.empty()` 兜底；或者直接把 `current` 改成按 `Level` 维度键分桶的 `Map<ResourceLocation, RecipeGraphCache>`，这样切换世界自动命中新缓存。
- 顺便监听 `RecipeManager` reload（`AddReloadListenerEvent` / `RecipesUpdatedEvent`）触发 `clear()`，目前换数据包完全不重建。

### 2. `RecipeSelectionMemory` 的 key 拼接极易碰撞
`key(ItemStack)` 现在拼的是 `levelKey + itemId + "|" + stack.getTag()`：
- `toString()` 一个 `CompoundTag` 不稳定，也带换行和 `=`，做 Properties key 不安全。
- 维度前缀只在 `minecraft.level != null` 时拼接，意味着同一物品在"主世界存档"和"进入世界的瞬间"会落到不同 key，记忆会"闪烁"。

**建议**
- 用 `stack.getTag() == null ? "" : stack.getTag().toString()` 之外再 hash 一下（例如 `Integer.toHexString(tag.hashCode())`），并把维度前缀无条件拼接（`level == null` 时用占位符 `"__loading__"`）。
- 配方选择本身没必要按维度区分（同一存档的配方表是统一的），建议**配方记忆不按维度分桶**，只有"替代品选择"才需要按维度，可以分两个 Properties 前缀。
- `save()` 每次 `remember` 都同步写盘，频繁切节点会抖动 IO。建议加 dirty 标记 + `clientTick` 末尾批量落盘，或 `Files.newOutputStream` 用 `StandardOpenOption.APPEND` 替换为内存缓存 + 退出时 flush。

### 3. 循环检测过度依赖 `path.add(key)` 的语义
`buildNode` 用 `Set<IngredientKey> path` + `path.add(key)` 的返回值判定环。这个写法是对的，但有一个边角：

```java
if (rememberedNode.isEmpty() && recipes.size() != 1) {
    path.remove(key);
    return node;   // 此时 node.recipe() == null
}
```

返回的 `TreeNode` 没设配也没有 children，UI 上它会被 `nodeFill` 判成"叶子"，但 tooltip 不会提示"无配方可选"。建议给 TreeNode 加一个 `noRecipe` 状态，UI 上显示成灰色 + "未选择配方"小标签（已有 lang key `screen.jeict.no_recipe` 没用上）。

### 4. `TreeNode.alternatives` 与 `displayStack` 不一致
`alternatives(...)` 会把每个 ItemStack 强行 `setCount(1)`；`replaceStack` 也是 `setCount(1)`。但 `RecipeNode.output()` 永远保留原 count。
在 `applyRecipe` 里 `counts.merge(childKey, childStack.getCount(), Integer::sum)`，`childStack` 此时还是 `alternatives[0]` 的原始 count（如果配方用 NBT 标签带 count 的会出错）。建议统一：进入 `counts` 之前一律 `setCount(1)` 并把 count 单独走通道，避免任何 NBT 标签带 count 的极端 mod 干扰。

---

## 二、性能与可扩展性

### 5. 布局算法重复遍历 + 指数级风险
`RecipeTreeScreen.layout` / `measure` / `childrenWidth` 在同一棵子树上**反复递归**，`render` 每帧调用 `rebuildLayout`：
- `measure(child)` 在 `layout()` 里又调一次（第 207 行），`childrenWidth` 内部又 `measure` 一遍，每帧复杂度近似 `O(N^2)`。
- 节点上 256 时问题不大，但配置里允许 `maxTreeNodes = 4096`，此时每帧要烧掉几百万次调用。

**建议**
- 把 `measure` 的结果缓存到 `IdentityHashMap<TreeNode, Integer>`，`rebuildLayout` 复用，只有当树结构变化时失效。
- `layoutNodes` 也用同一份缓存，避免每帧 `new ArrayList<>()`。
- `layoutFor(TreeNode)` 现在是 `O(N)` 线性搜，建议同时维护 `Map<TreeNode, LayoutNode>`。

### 6. 取消每帧全量重算
`render()` 里：`rebuildLayout()` + `costLayouts.clear()` + `renderTotals()` + 全树 layout。绝大多数帧之间树结构是没变的（玩家只是在拖视图 / 滚轮缩放）。

**建议**
- 引入 `dirtyLayout` / `dirtyCosts` 标记：`mouseClicked` / `mouseScrolled` / `transferRecipeFromJei` 才置 dirty，`render` 只在 dirty 时重建。
- 平移 / 缩放只影响 `panX/panY/zoom`，完全可以增量更新 `LayoutNode` 的屏幕坐标，不需要重跑 `measure`。

### 7. 命中测试与 `rebuildLayout` 解耦
`mouseClicked` / `renderNodeTooltip` 都遍历 `layoutNodes` 和 `costLayouts`，这些列表在 `render` 时重建。如果将来想做"非每帧重建"，把 `layoutNodes` 提到字段并在 `init`/`rebuild` 时机维护，比散落在 render 里更稳。

### 8. JEI 配方图标 / 预览缓存
`recipeIconCache` 和 `recipePreviewCache` 是 `Map<ResourceLocation, Optional<...>>`：
- 两者都是 `computeIfAbsent`，但生命周期跟 `RecipeTreeScreen` 一样长。一棵 4000 节点的树可能占 4000 个 `IRecipeLayoutDrawable`，每个 drawable 都持有 GL 资源，容易爆显存。
- `IRecipeLayoutDrawable` 用完后应该调用 JEI 提供的释放路径（如果有的话），或者直接 LRU 截断，例如 `Caffeine` 风格的 maximumSize。

**建议**
- 给 `recipePreviewCache` 上 size 上限（例如 32），用 `LinkedHashMap` 的 `removeEldestEntry`；预览只在悬浮 tooltip 时才生成，避免主动渲染整树的所有预览。
- `rebuildSelectedNode` / `selectAlternative` 时 `recipeIconCache.clear()` + `recipePreviewCache.clear()` 是全清，比较暴力；可以只清除受影响子树的条目。但考虑到实现成本，先保持全清也行，重点是加上限。

---

## 三、用户体验

### 9. 一键放入合成台 / 物品栏一键收集
模组目前只能"查看"，没有把"算好的批次数 → 实际去合成"落地。这是 JEICT 从"看板"升级到"工作流"的关键一步。

**建议**
- 在总耗材条上为每个叶子材料加一个 ``[+]`` 按钮，调用 `Minecraft.getInstance().gameMode.handleInventoryMouseClick` 把对应物品从玩家背包"提取"到 3x3 合成台的对应格子里（需要打开工作台）。或者更现实一点：把每个**叶子节点**挂一个"跳转到 JEI 该配方 + 把焦点放进 JEI 转移按钮"。
- 或者走 JEI 的 `IRecipeTransferHandler`，把当前节点 `TreeNode.recipe()` 直接 transfer 到打开的 Containers（如 `CraftingMenu`、`Inventory`）。`JeiTreePlugin` 已经注册了 universal transfer handler，但只是做"JEI → 树"方向，反向"树 → 工作台"是空的。

### 10. 批次编辑目前仅在 root 可调
`canEditBatches(node)` 限制成 `node == tree.root() && node.recipe() != null`。这是有道理的（子节点批量随父节点联动），但玩家心理模型上经常希望"我再多做 4 个木板" → 在木板节点上滚轮加 4。

**建议**
- 子节点批次编辑可以做成"局部 overdrive"：把它转换成父节点 batch 的等效倍率，再写入根节点 batch。或者直接在子节点显示一个 `+1 批` 的按钮，背后是对 root batch 加 `(child.outputPerBatch / root.outputPerBatch)` 向上取整。需要文案上让用户理解"加一个子节点批次相当于加多少根节点批次"。

### 11. 折叠/展开只对同 key 节点统一操作
`setExpandedForMatchingNodes` 是按 `key` 匹配的，意味着你按下木板节点的折叠，全树所有木板节点都折叠。这有时候不是用户想要的——例如"只想折叠这条分支的木板"。

**建议**
- 提供两种模式：默认折叠/展开只影响当前节点（`node.expanded()` 翻转）；按 `Shift` + 右键再触发"同 key 全部折叠/展开"。当前只有"全部"这个粗暴档位。
- 子树折叠也建议加：右键节点的菜单里加"折叠此分支"（递归把所有后代 `expanded=false`），对深度可达 8 的树非常实用。

### 12. 平移与缩放交互的细节
- `mouseScrolled` 在没有 hover 节点时直接 `panY += delta * 28`，Shift 时走 `panX`，但 Ctrl 是缩放——三组修饰符有点撞车，玩家经常误操作。
- 右键拖拽平移和"右键节点折叠"在同一事件流里，鼠标在节点上时右键是折叠、离开节点时右键才是平移，行为对玩家来说隐式。
- 缩放没有"重置视图"按钮。

**建议**
- 加一个工具栏（顶部或左上角）：`[+] [-] [恢复视图] [居中] [折叠全部] [展开到深度 N]`。把"隐藏交互"显式化。
- 右键空白处平移 / 右键节点弹出一个小菜单（折叠此节点 / 折叠此分支 / 复制物品 / 用 JEI 看配方 等），比直接折叠体验更好。
- 现在的 `renderHelp` 在左上角堆_help 文字，建议折叠成可关闭的帮助面板（按 `H` 显示/隐藏），否则常驻会遮挡大树。

### 13. 节点颜色仅按深度循环
`nodeFill` 按 `depth % 4` 切色，但同深度内配方节点（`recipe()==null` 的叶子是橙系，有 recipe 的是另一种橙）色差只有 1 位。颜色辨识度低。

**建议**
- 引入语义色：叶子节点（无配方）=灰色、可合成=蓝色、循环=黄、受限=橙色、已被库存足够=绿色描边。颜色信息密度比深度更有用。
- 也可以把"库存对比结果"映射到节点描边：`INVENTORY_*_BORDER` 已经定义，现在只用在 cost 条上，节点本身没用。

### 14. 库存对比面板的占用
`InventoryCompareEntry` 在 `renderTotals` 里被填充，但目前每个 CostDisplayEntry 都会 push 进去（包括 leftovers）。`renderInventoryComparePanel` 又会过滤一遍。

**建议**
- leftovers 不该出现在库存对比里（剩余物不需要"已有"对比），可以在源头就分流。
- 加分页 / 滚动：现在超过 `INVENTORY_PANEL_MAX_ROWS = 8` 行只显示"+N 个更多材料"。改成可滚动的列表更适合大型合成。

### 15. 多语言、Paulibolation 之外的 Recipe Source
mod hard-dependency `jei`。如果玩家用 REI 或 EMI，没用。1.20.1 的生态里 EMI 的市场占有率不低。

**建议**
- 长期可以抽出一个 `RecipeSource` 接口，JEI 实现一个，EMI 实现一个；现在只 JEI 也合理，但 README/描述里明确"需要 JEI"避免误解。
- 短期：检测 JEI 是否存在（`ModList.get().isLoaded("jei")`），如果没装就静默禁用 keybind 而不是崩。

---

## 四、代码质量与可维护性

### 16. `RecipeTreeScreen` 接近 2000 行单文件
这是最大的可维护性债。建议按职责拆：
- `RecipeTreeLayout`（measure + layout）
- `RecipeTreeRenderer`（renderBranches / renderNodes / renderNodeTooltip）
- `InventoryComparePanel`（renderInventoryComparePanel + 相关辅助）
- `CostSummary`（renderTotals + collectCosts + CostEntry）
- `RecipeTransferController`（transferRecipeFromJei + applyRecipeSelection + collectInputs）

拆完单文件 ≤ 500 行，单元测试也更容易补。

### 17. 内部类/数据类过多堆在文件尾部
`CostLayout` `InventoryCompareEntry` `ButtonLayout` `FilterLayout` `JeiRecipeMatch` `InputData` 等都堆在 `RecipeTreeScreen.java` 末尾。建议提到 `com.lhy.jeict.client.screen.layout` 包，便于复用与测试。

### 18. 魔法值到处都是
`0xFF1488A6` `0xFF11A781` 等颜色、`scale(7)` `scale(28)` `scale(LEVEL_GAP)` 等数值散落。建议集中到 `RecipeTreeTheme` / `RecipeTreeMetrics` 两个常量类，便于后续做皮肤（暗色 / 浅色 / 高对比度）和 1.21 port 时的尺寸调整。

### 19. 配置项运行期不可热改
`Config` 的 `onLoad` 只在 `ModConfigEvent` 时拷贝一次，游戏内改 config 不会立即生效。建议用 `ForgeConfigSpec.IntValue` 的 getter 直接访问，或在 `OnConfigReload` 里也 `clear()` 缓存。

### 20. 国际化有些硬编码中文
`renderHelp` 里直接 `graphics.drawString(font, "左键：打开 JEI 选择下级配方", ...)`，没走 lang。英文玩家开了这个 mod 会看到中文。**必改**：
```java
graphics.drawString(font, Component.translatable("screen.jeict.help.left_click"), ...);
```
其它散落的 "x" 拼数字也建议统一走 lang。

---

## 五、面向未来的小步快跑

如果只想挑 **3 件事** 先做，我会选：

1. **RecipeGraphCache 在世界切换/reload 时失效**（架构正确性，10 行代码搞定，避免潜在崩溃与数据错位）。
2. **layout 结果缓存 + dirty 标记**（性能，从每帧 O(N²) 降到首帧 O(N²)、其余帧 O(N)，大树上立刻能感觉到）。
3. **`renderHelp` 中文化 i18n + 节点折叠改成单点操作**（用户体验 + 国际化最小修复，零风险）。

后续再做：拆文件（16）、库存对比面板滚动（14）、与 EMI 的 RecipeSource 解耦（15）。

---

## 附：可直接落地的两张快速 win 清单

| 文件 | 修改点 | 影响 |
|---|---|---|
| `RecipeGraphCache.java` | 监听 `RecipesUpdatedEvent` / `LevelEvent.Unload` `clear()` | 修复换世界数据错位 |
| `RecipeGraphCache.java` | `get()` 加主线程检查 | 防御性 |
| `RecipeSelectionMemory.java` | `key()` 维度前缀恒定 + tag hash | 避免记忆闪烁 |
| `RecipeSelectionMemory.java` | `save()` 加 dirty + tick flush | 减少 IO 抖动 |
| `RecipeTreeScreen.java` | layout 缓存 + dirty 标记 | 性能 |
| `RecipeTreeScreen.java` | `renderHelp` 文案走 lang | 国际化 |
| `RecipeTreeScreen.java` | 折叠默认只切当前节点，Shift 才全选 | 体验 |
| `RecipeTreeScreen#nodeFill` | 语义色 + 库存对比描边 | 体验 |
| `Config.java` | 配置热更（直接 getter） | 体验 |
| `mods.toml` / `Jeict.java` | soft-depend jei 检测，缺失时禁用 keybind | 兼容性 |
