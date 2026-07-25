# JEI Crafting Tree

[English](#english) | [中文](#中文)

**Current release:** `v0.0.1` · [Download from GitHub Releases](https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.1) · [Changelog](CHANGELOG.md)

JEI Crafting Tree is a NeoForge extension for JEI that turns a selected recipe into an interactive, recursive production plan. It works as a standalone client-side planning tool and exposes integration APIs for storage networks, exact pattern detection, pattern encoding, and machine-aware inventory sources.

---

## English

### Overview

JEI Crafting Tree adds an **Open Crafting Tree** action to JEI recipe layouts. From a single recipe, the tree can expand selected ingredient routes, combine multiple targets into one global plan, allocate available inventory, account for secondary outputs, and produce an ordered execution checklist.

The planning model is independent from AE2. Optional backends such as AE2 Utility can add exact-pattern hints, encoding, upload, substitution controls, reusable-input rules, and machine identifiers without making AE2 a hard dependency of this mod.

### Core capabilities

#### Recursive recipe exploration

- Expands JEI recipes into a navigable dependency tree.
- Supports normal graph and layer-merged material views.
- Lets the user select a child recipe or an alternative ingredient at each input. Recipe choices and manual collapse state are synchronized across visible occurrences of the same material at different tree depths.
- Can automatically expand unresolved inputs when JEI exposes exactly one usable recipe.
- Detects recursive routes, stops unsafe expansion, highlights the affected incoming edge and material entry, and reports cycle diagnostics instead of recursing indefinitely.

#### Global multi-project planning

- Maintains multiple named production targets in one session.
- Supports a spatial multi-tree workspace: complete trees are rendered together on one shared canvas and every visible tree can be edited directly without switching views. New independent trees can be added above, below, left, or right, and the JEI bookmark-side shortcut returns to the last workspace. Each tree owns a separate material, surplus, draft, history, and planning state.
- Uses a shared global material ledger across all projects.
- Reuses overproduction from one branch or project in another branch or project.
- Credits secondary outputs and byproducts back to the global supply pool.
- Uses `long` quantities throughout the planning layer and saturates arithmetic at `Long.MAX_VALUE`.

#### Recipe semantics

- Imports all outputs exposed by a JEI recipe, with one focused primary output and optional secondary outputs.
- Supports item, fluid, chemical, and custom JEI ingredient identities; non-item quantities use the same bottom-right count-overlay style as item stacks.
- Treats JEI catalyst slots and backend-declared reusable inputs as non-consumed requirements.
- Distinguishes “this output is craftable” from “this exact selected recipe already has a matching pattern.”

#### Editable AE2 pattern drafts

When AE2 and a compatible backend such as AE2 Utility are installed, the node inspector includes a compact pattern editor based on AE2's native pattern-mode and slot textures. AE2 remains optional: the editor is not created or rendered when the integration is unavailable.

- Creates one editable draft for each exact selected recipe and shares that draft across repeated occurrences of the same recipe.
- Previews the exact inputs, primary output, secondary outputs, quantities, alternatives, and substitution state that will be sent for encoding.
- Supports AE2 processing-pattern capacity of up to 81 input slots and 27 output slots through a paged 3×3 input / three-output viewport and scrollbar.
- Allows processing slots to be replaced from the held item, cleared with right click, or assigned an exact amount with middle click or `Ctrl + wheel`.
- Cycles legal JEI alternatives, promotes a secondary output to the primary output, removes unwanted byproducts, clears the processing draft, and restores the original recipe snapshot.
- Keeps structured crafting drafts recipe-safe: their slots can cycle legal alternatives but cannot be freely removed, replaced, or assigned arbitrary amounts.
- Shows dirty, invalid, removed-input, removed-output, and changed-primary-output states before encoding.
- Uses the edited draft—not the immutable JEI recipe snapshot—for batch deduplication, validation, encoding, and upload.
- Performs lightweight client checks first and requires the backend/server to validate slot limits, ingredients, quantities, and the primary output before consuming a blank pattern.

#### Inventory allocation

Built-in inventory coverage includes:

- player main inventory;
- armor and offhand slots;
- non-player slots in the currently open container.

External mods can register additional `InventorySource` implementations for AE2, Refined Storage, remote warehouses, or other storage systems. Sources are priority ordered, versioned, cached, and isolated so a failing integration does not break the planner.

#### Alternative-material strategies

The planner provides five allocation modes:

| Strategy | Behavior |
| --- | --- |
| `LOCKED` | Uses the alternative currently selected in the tree. |
| `MIX_AVAILABLE` | Consumes matching alternatives from available inventory before reporting or crafting the remainder. |
| `MOST_AVAILABLE` | Prefers the alternative with the largest available stock. |
| `PREFERRED_NAMESPACE` | Prefers materials from the configured mod namespace. |
| `STRICT_COMPONENTS` | Keeps the exact selected component/subtype identity. |

The active strategy can be changed from the overview settings panel or the client configuration.

#### Plan report

The **Plan** screen contains four projections of the same immutable planning result:

- **Materials** — raw requirements, allocated inventory, inventory-source status, surplus/byproducts, and cycle diagnostics.
- **Checklist** — ordered crafting and machine execution steps.
- **Machines** — total runs grouped by machine identifier.
- **Routes** — background comparison of all substitution strategies, with a deterministic recommendation based on raw inputs, machine runs, machine count, waste, and step count.

#### Search and navigation

- Searches recipe names, stable recipe identities, ingredients, mods, and machines.
- `@namespace` narrows searches toward a mod namespace.
- `#machine` narrows searches toward a machine name.
- Matching nodes use the active theme accent.
- When Just Enough Characters is installed, names and machine labels also support its configured pinyin matching rules.
- Enter focuses the next result; the search key can focus the field from anywhere in the overview.

#### Recipe memory and edit history

- Remembers selected recipes and manually collapsed branches.
- Uses versioned memory keys containing the parent recipe, node path, input slot, ingredient identity, memory scope, and modpack fingerprint.
- Supports global, server, and world memory scopes.
- Automatically reads legacy unscoped entries and migrates them when they are successfully resolved.
- Supports undo and redo for tree selections, expansion state, alternative choices, project creation/removal, project selection, and target amounts.
- Stores up to 64 in-session history snapshots.

#### Performance design

- Runs the global solver on a dedicated background thread.
- Normalizes custom JEI ingredient renderers into a centered 16×16 slot, renders fluid icons at a consistent full fill level, and keeps quantities outside the icon area.
- Caches scoped recipe-memory profiles and collapsed-state lookups, avoids formatter-heavy hashing, and skips repeated traversal of shared graph nodes during unique-recipe expansion.
- Route comparison reuses the already computed active-strategy result and cancels obsolete workers instead of solving every route from scratch.
- Cancels superseded planning requests and prevents stale results from replacing a newer tree.
- Checks interruption during long planning traversals.
- Uses immutable planning snapshots before entering background work.
- Replans only when the tree/configuration fingerprint or inventory version changes.
- Uses versioned inventory snapshot caching.
- Caches JEI output lookups and recipe-ID indexes per JEI runtime.
- Limits lookup result counts and unique-recipe expansion work per tick.
- Uses visible-region culling, row indexes, and render-data caches in the overview.
- Propagates large quantities with saturating arithmetic and reuses the result during one layout refresh.
- Aggregates repeated branches in merged-layer and top-material traversals instead of visiting the same node once per parent link.
- Canonicalizes equivalent immediate branches in the merged projection while leaving the editable recipe tree unchanged.
- Preserves shared DAG branches in undo/redo snapshots, preventing repeated 3x3 compression inputs from expanding exponentially in memory.
- Keeps search filtering render-local instead of rebuilding the complete tree.
- Renders the floating material panel from one GUI render event to prevent duplicate drawing.

### Usage

1. Open any recipe in JEI.
2. Click the JEI Crafting Tree button in the recipe layout.
3. Expand unresolved ingredients and choose the desired child recipes or alternatives.
4. Use **Projects** to add targets and set their requested quantities.
5. Choose an alternative-material strategy from the settings panel when needed.
6. Open **Plan** to inspect materials, execution order, machine runs, and route comparisons.
7. Use the floating material panel to keep requirements visible while browsing JEI or other screens.
8. If a compatible backend is installed, use the exact-pattern, encode, or upload actions supplied by that backend.

### Controls

| Input | Action |
| --- | --- |
| Left click | Select, expand, inspect, or activate the control under the pointer. |
| Right click | Change a recipe or open the corresponding JEI recipe/usage view. |
| Mouse wheel | Zoom or scroll the active panel. |
| Shift + wheel | Horizontal navigation where supported. |
| Ctrl + wheel | Zoom the merged view or scale the floating material panel. |
| Enter in search | Focus the next matching result. |
| `F` | Focus search. |
| `Ctrl+Z` | Undo the last edit. |
| `Ctrl+Y` | Redo the last undone edit. |

Key bindings can be reassigned in Minecraft's Controls screen.

### Client configuration

NeoForge writes the client configuration to `config/jeict-client.toml`.

| Key | Default | Purpose |
| --- | ---: | --- |
| `planning.rememberSelections` | `true` | Enables reading and writing recipe/collapse memory. |
| `planning.autoMergeMaterials` | `true` | Opens the overview in layer-merged material mode. |
| `planning.computeQuantities` | `true` | Enables rolled-up quantities and pattern-count calculations. |
| `planning.autoExpandUniqueRecipes` | `false` | Automatically expands inputs with one usable recipe. |
| `planning.substitutionStrategy` | `LOCKED` | Default alternative-material allocation strategy. |
| `planning.preferredNamespace` | empty | Namespace preferred by `PREFERRED_NAMESPACE`. |
| `planning.memoryScope` | `SERVER` | Memory isolation level: `GLOBAL`, `SERVER`, or `WORLD`. |
| `planning.memoryProfile` | empty | Optional manual modpack/profile identifier; otherwise a mod-list fingerprint is used. |
| `performance.showFloatingMaterials` | `true` | Enables the floating material requirements panel. |
| `performance.maxAutoExpandStepsPerTick` | `32` | Limits unique-recipe expansion work performed in one client tick. |
| `performance.maxRecipeLookupResults` | `512` | Caps cached JEI recipe lookup results per output query. |

### Integration API

#### Crafting backend

Register one `CraftingTreeBackend` through `CraftingTreeBackends.register(...)`.

Important extension points include:

- `isOutputCraftable(...)` for broad output availability;
- `hasExactPattern(...)` and `exactPatternFingerprint(...)` for the currently selected recipe route;
- `isReusableInput(...)` for tools, molds, containers, or catalysts;
- `machineId(...)` for execution and machine-run reports;
- encode/upload capability and actions;
- optional item/fluid substitution controls, rendered with JEICT-native labeled buttons beside the encoding actions.

New integrations should implement exact pattern matching from a normalized recipe fingerprint rather than treating every pattern that produces the same output as equivalent.

#### Inventory source

Register storage through `CraftingTreeInventorySources.register(...)`:

```java
CraftingTreeInventorySources.register(new InventorySource() {
    @Override
    public String id() {
        return "example:network";
    }

    @Override
    public long version() {
        return networkChangeCounter;
    }

    @Override
    public List<InventoryAmount> snapshot() {
        return immutableAmounts;
    }
});
```

`version()` should change only when visible stock changes. `snapshot()` should return an immutable or safely copied view. Material identities use `MaterialKey`, which includes the JEI ingredient type and subtype-aware UID.

### Requirements

JEI Crafting Tree is a client-side mod. Install it on the client together with JEI; dedicated servers do not
need it. Installing the jar on a dedicated server is harmless because its mod entry point is client-only.
The mod does not register a mandatory client/server network channel, so clients can join servers without JEI
Crafting Tree installed. Optional encoding or upload integrations may still have their own server-side requirements.

- Minecraft `1.21.1`
- NeoForge `21.1.233` or newer in the compatible `21.1.x` line
- Java `21`
- JEI `19.21.0` or newer; release `v0.0.1` is tested with JEI `19.27.0.340`

Optional:

- AE2 Utility `1.6.0` or newer for an external AE2-oriented backend, when installed and compatible.
- Just Enough Characters for optional pinyin matching in the overview search field.

### Build and test

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Artifacts are written to `build/libs/`. A faster Java-only verification is available through `./gradlew compileJava` or `.\gradlew.bat compileJava`.

### Compatibility notes

- The planner can understand generic JEI ingredient identities, but visible rendering and external stock detection still depend on JEI and the installed integrations exposing those ingredient types correctly.
- Exact-pattern accuracy depends on the backend implementing `hasExactPattern(...)` with a route-sensitive fingerprint. The default compatibility implementation only preserves older backend behavior.
- Route comparison evaluates the currently selected recipe tree under different material-allocation strategies; it does not enumerate every possible JEI recipe combination automatically.
- Planning is client-side and does not move items or execute machines by itself.

### License

JEI Crafting Tree is licensed under the [MIT License](LICENSE).

---

## 中文

### 项目简介

JEI Crafting Tree 是面向 Minecraft 1.21.1 / NeoForge 的 JEI 配方规划扩展。它可以把 JEI 中选中的配方转换为可交互的递归配方树，并进一步建立多目标共享的全局生产计划。

模组本体不依赖 AE2。安装兼容后端后，可以在同一界面中获得精确样板检测、样板编码、样板上传、可复用输入判定、机器标识以及外部库存接入能力。

### 主要功能

- **递归配方树**：逐层展开原料配方，支持普通图形视图与同层材料合并视图；同一材料在不同深度出现时，会同步配方选择和手动折叠状态。
- **多配方树工作区**：多棵完整配方树会同时显示在同一个可缩放、可拖动的画布中，每棵树都能在原位置直接选择配方、折叠分支、打开节点详情和编辑样板，无需切换当前树。可从远离树体的上、下、左、右纯文字入口添加新树；JEI 书签区旁的快捷按钮可返回上次工作区。每棵树的总材料、剩余材料、项目、样板草稿、编辑历史和规划结果完全隔离。
- **全局材料账本**：所有项目和分支共享库存、余料与副产物，避免按节点重复统计。
- **多目标项目**：可以建立多个命名目标、设置 `long` 类型目标数量，并在项目之间切换编辑。
- **多输出与副产物**：读取 JEI 暴露的全部输出，将次要输出重新计入全局供给。
- **催化剂与可复用输入**：JEI catalyst 槽位和后端声明的模具、工具、容器等不会按合成次数重复消耗。
- **通用材料身份**：支持物品、流体、化学品及其他 JEI 自定义 ingredient type，并保留 subtype 身份；非物品材料数量与物品一样使用右下角角标。
- **替代材料策略**：提供锁定、混用库存、库存最多、优先模组命名空间和严格组件五种策略。
- **库存聚合**：内置玩家背包、护甲、副手和当前容器；外部模组可以注册网络库存来源。
- **计划报告**：集中展示基础原料、库存分配、余料/副产物、循环依赖、执行清单、机器运行次数和路线比较。
- **精确已有样板判断**：区分“该输出可合成”和“当前选中的精确配方已有样板”；禁用已有样板展开后，会立即折叠已展开分支，并阻止手动选择、配方记忆或唯一配方逻辑再次展开。
- **搜索与定位**：支持材料、配方、`@模组`、`#机器` 搜索，Enter 定位下一个结果；安装 Just Enough Characters 后兼容其拼音匹配规则。
- **节点详情与样板草稿编辑**：右键节点可查看材料、数量、机器和已有样板状态。安装 AE2 与兼容后端后，详情面板使用 AE2 原生样板模式背景、槽位和控制图标，直接编辑最终要写入样板的输入、输出、数量与替换状态；未安装 AE2 时不创建也不绘制该区域。
- **配方记忆**：按父配方、节点路径、输入槽、材料身份、服务器/世界和整合包指纹隔离记忆，并兼容迁移旧记录。
- **撤销与重做**：覆盖配方选择、展开/折叠、替代材料和项目管理操作，最多保存 64 个会话内快照。
- **悬浮材料面板**：可固定材料清单，在其他界面中继续查看并跳转 JEI 配方或用途。
- **外部后端 API**：支持存储网络、精确样板、编码、上传、机器统计和库存来源扩展。

### AE2 样板草稿编辑

节点详情中的样板区不是只读预览，而是批量编码与上传使用的最终草稿：

- 相同精确配方在树中多次出现时共享同一份草稿，任一位置的修改都会同步生效；
- 处理样板支持最多 81 个输入槽和 27 个输出槽，通过 3×3 输入、三个输出的可滚动视窗编辑；
- 左键可轮换 JEI 提供的合法备选原料，手持物品时可填入或替换处理样板槽位；
- 右键可清除处理样板槽位，中键可输入精确数量，`Ctrl + 滚轮` 可快速调整数量；
- 可切换主要输出、把次要输出提升为主输出、删除不需要的副产物、清空草稿并恢复原始配方；
- 可单独设置物品替换、流体替换和输入顺序保留状态；
- 合成、锻造和切石等结构化配方保持配方安全，只允许切换合法备选，不允许自由删除、替换或修改数量；
- 编辑器会明确提示草稿已修改、无效、删除输入、删除输出或主要输出变化；
- 批量收集按最终草稿指纹去重，编码和上传严格使用草稿内容，不会退回未修改的 JEI 配方快照；
- 客户端先执行轻量校验，兼容后端与服务端还会再次检查槽位上限、材料类型、数量和主要输出，校验失败时不会消耗空白样板。

删除副产物只影响最终编码草稿，不会篡改配方树用于材料规划的真实产出与余料语义。

### 全局计划规则

规划器使用统一供给账本处理全部目标：

1. 优先消耗已有余料和副产物；
2. 再分配已注册库存来源中的可用数量；
3. 根据所选替代策略确定输入材料；
4. 计算配方运行次数，并把全部输出写回供给账本；
5. 无法继续展开的需求计入基础原料；
6. 后续产生的副产物可以抵消此前登记的同类基础原料缺口；
7. 循环路线会写入诊断结果，而不会无限递归。

所有规划数量均使用 `long`，溢出时采用饱和运算。

### 替代材料策略

| 策略 | 行为 |
| --- | --- |
| `LOCKED` | 只使用配方树中当前选中的材料。 |
| `MIX_AVAILABLE` | 优先混合消耗库存中的所有匹配替代材料，再处理剩余需求。 |
| `MOST_AVAILABLE` | 优先选择库存数量最多的替代材料。 |
| `PREFERRED_NAMESPACE` | 优先选择配置中指定模组命名空间的材料。 |
| `STRICT_COMPONENTS` | 严格保持当前选中的组件与 subtype 身份。 |

可以在总览设置栏中即时切换策略，也可以修改客户端配置作为默认值。

### 使用方法

1. 在 JEI 中打开任意配方。
2. 点击配方布局中的 JEI Crafting Tree 按钮。
3. 展开未解析原料，并选择需要的下级配方或替代材料。
4. 打开 **项目**，添加生产目标并设置目标数量。
5. 根据需要从设置栏选择替代材料策略。
6. 打开 **计划**，查看材料、执行清单、机器运行统计与路线比较。
7. 可将材料需求固定到悬浮面板，在浏览其他界面时继续查看。
8. 安装兼容后端后，可使用后端提供的样板提示、编码和上传功能。

### 操作与快捷键

| 操作 | 功能 |
| --- | --- |
| 左键 | 选择下级配方或点击控件；禁用已有样板展开时，已有样板节点不会再打开配方选择。 |
| 右键 | 打开节点详情；材料清单与悬浮面板中的右键操作可跳转 JEI 配方/用途。 |
| 鼠标滚轮 | 缩放或滚动当前面板。 |
| Shift + 滚轮 | 在支持的区域横向移动。 |
| Ctrl + 滚轮 | 缩放合并视图或调整悬浮面板比例。 |
| 搜索框内 Enter | 定位下一个匹配结果。 |
| `F` | 聚焦搜索框。 |
| `Ctrl+Z` | 撤销。 |
| `Ctrl+Y` | 重做。 |

快捷键可以在 Minecraft 的按键设置中重新绑定。

### 客户端配置

NeoForge 会将配置写入 `config/jeict-client.toml`。

| 配置项 | 默认值 | 作用 |
| --- | ---: | --- |
| `planning.rememberSelections` | `true` | 启用配方选择和折叠状态的读取与写入。 |
| `planning.autoMergeMaterials` | `true` | 默认使用同层材料合并视图。 |
| `planning.computeQuantities` | `true` | 启用汇总数量和所需样板数量计算。 |
| `planning.autoExpandUniqueRecipes` | `false` | 自动展开只有一个可用配方的输入。 |
| `planning.substitutionStrategy` | `LOCKED` | 默认替代材料分配策略。 |
| `planning.preferredNamespace` | 空 | `PREFERRED_NAMESPACE` 优先使用的模组命名空间。 |
| `planning.memoryScope` | `SERVER` | 记忆范围：`GLOBAL`、`SERVER` 或 `WORLD`。 |
| `planning.memoryProfile` | 空 | 手动整合包/配置档标识；留空时自动计算模组列表指纹。 |
| `performance.showFloatingMaterials` | `true` | 是否显示悬浮材料面板。 |
| `performance.maxAutoExpandStepsPerTick` | `32` | 每个客户端 tick 最多处理的自动展开步骤。 |
| `performance.maxRecipeLookupResults` | `512` | 单次 JEI 输出查询最多缓存的配方数量。 |

### 性能与稳定性

- 全局规划在独立后台线程执行，不阻塞渲染线程。
- 新请求会取消旧请求，并通过代次校验阻止旧结果覆盖新树。
- 长规划循环会检查线程中断，以便更快取消。
- 树、配置或库存版本没有变化时不会重复提交规划。
- 库存快照、JEI 输出查询和配方 ID 索引均带缓存与失效机制。
- 自动展开和 JEI 查询都有可配置上限。
- 总览使用可见区域裁剪、行索引和渲染数据缓存。
- 节点详情的样板终端预览仅在 AE2 已加载时使用其原生纹理，最多缓存并绘制 9 个输入槽和 3 个输出槽；预览不查询 JEI 或存储网络，也不扫描整棵树。
- 大数量传播使用饱和算术，并在一次布局刷新内复用相同计算结果，避免重复乘法和溢出。
- 合并视图与顶部材料收集会聚合同一层级的重复分支，避免同一节点被多个父链接重复遍历。
- 合并视图只对显示投影做等价即时分支归一化，不修改可编辑配方树、配方选择或记忆数据。
- 撤销/重做快照会保留配方树中的共享 DAG 分支，避免 3×3 重复输入在九重压缩链中被指数级复制并耗尽内存。
- 搜索仅过滤当前渲染结果，不会因为每次输入而重建整棵树。
- 外部库存来源发生异常时会被隔离，不会中断其他来源和规划流程。

### 外部集成

#### 配方后端

外部模组可以通过 `CraftingTreeBackends.register(...)` 注册一个 `CraftingTreeBackend`。建议重点实现：

- `isOutputCraftable(...)`：判断某类输出是否可由网络生产；
- `hasExactPattern(...)`：判断当前选中的精确配方是否已有样板；
- `exactPatternFingerprint(...)`：提供稳定、规范化的精确样板指纹；
- `isReusableInput(...)`：标记模具、工具、容器或催化剂；
- `machineId(...)`：为执行清单和机器统计提供稳定机器标识；
- 样板编码、上传，以及位于编码操作左侧、采用 JEICT 原生样式的“物品替换”和“流体替换”开关。

精确样板实现不应只比较输出物，而应把配方身份、规范化输入、输出以及后端需要的处理模式纳入指纹。

#### 库存来源

外部存储系统可以实现 `InventorySource`，并通过 `CraftingTreeInventorySources.register(...)` 注册。来源应提供稳定 ID、优先级、库存版本和不可变快照。库存版本只应在规划器可见库存发生变化时更新。

### 运行要求

JEI Crafting Tree 是客户端模组，只需要与 JEI 一起安装在客户端；专用服务端无需安装。即使把 jar
放入专用服务端，客户端限定的模组入口也不会在服务端加载。本模组没有注册强制双端存在的网络通道，
因此客户端可以正常进入未安装 JEI Crafting Tree 的服务器。样板编码、上传等可选兼容后端可能仍有其
自身的服务端安装要求。

- Minecraft `1.21.1`
- NeoForge `21.1.233` 或兼容的更新版本
- Java `21`
- JEI `19.21.0` 或更新版本；正式版 `v0.0.1` 已使用 JEI `19.27.0.340` 测试

可选集成：

- AE2 Utility `1.6.0` 或更新的兼容版本，可作为 AE2 功能后端。
- Just Enough Characters，可为总览搜索框提供拼音匹配。

### 构建与测试

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

构建产物位于 `build/libs/`。仅进行 Java 编译检查时可执行：

```powershell
.\gradlew.bat compileJava
```

### 兼容性说明

- 通用材料规划依赖 JEI 和对应模组正确暴露 ingredient type、subtype 与配方槽位。
- 精确样板判断的准确性取决于外部后端是否实现了路线敏感的 `hasExactPattern(...)`。
- 路线比较是在当前已选择的配方树上比较不同材料分配策略，不会自动穷举 JEI 中所有配方组合。
- 规划器只生成客户端计划，不会自行移动物品或执行机器。

### 许可证

本项目使用 [MIT License](LICENSE)。
