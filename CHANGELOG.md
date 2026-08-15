# Changelog

[v0.0.4](#v004) | [v0.0.3](#v003) | [v0.0.2](#v002) | [v0.0.1](#v001--2026-07-23)

## v0.0.4

### English

#### Fixed

1. Fixed batch-count scrolling in the multi-tree workspace.

### 中文

#### 修复

1. 修复多配方树工作区中无法通过滚轮调节批次的问题。

## v0.0.3

### English

#### Added

1. Added optional server-side batch crafting channels for AE2 and Sophisticated crafting interfaces.
2. Added Creative-mode material refill to the floating material panel. When JEICT is installed on both sides, missing item materials can be inserted into compatible slots of the open menu, with server validation and a per-click limit of 64 changed slots.

#### Optimized

1. Improved large-quantity auto-crafting planning.
2. Improved floating material projection so intermediate products are expanded only as needed by the current inventory, reducing unnecessary material entries and recalculation work.

#### Fixed

1. Fixed auto-crafting from the floating material panel failing in Sophisticated Storage, Sophisticated Backpacks, and ME terminals.
2. Fixed alternative-material changes not being synchronized into existing pattern drafts before encoding or uploading.
3. Fixed the overview splitting the same displayed material into separate aggregates when it appeared with different candidate sets or as both a recipe output and a leaf input.
4. Fixed remembered child-recipe selections not being applied when the same material appeared elsewhere in the tree; selections now fall back by material signature.
5. Fixed pattern encoding/upload under AE2 Utility still using the previously selected material after switching alternatives in the tree.
6. Fixed AE2 Utility integration hiding the JEI Crafting Tree button even when the utility's own encoding arrow was unavailable.

### 中文

#### 新增

1. 新增 AE2 与 Sophisticated 合成界面的可选服务端批量合成通道。
2. 新增悬浮材料面板的创造模式补料功能。客户端和服务端都安装 JEICT 时，可按缺失数量向当前菜单的兼容槽位补充物品；服务端会验证请求，每次点击最多修改 64 个槽位。

#### 优化

1. 优化大数量自动合成规划。
2. 优化悬浮材料投影：根据当前库存按需展开中间产物，减少不必要的材料条目和重复计算。

#### 修复

1. 修复材料清单悬浮界面自动合成在精妙存储、精妙背包和 ME 终端无法正常生效的问题。
2. 修复切换替代材料后，编码或上传前现有样板草稿没有同步新材料的问题。
3. 修复总览中同一材料因候选集合不同、或同时作为配方输出与叶子输入而被拆分成多条汇总的问题。
4. 修复已记忆的下级配方选择在树的其他位置遇到相同材料时不生效的问题；选择现在按材料签名兜底。
5. 修复 AE2 Utility 下切换替代材料后，编码/上传仍使用切换前材料的问题。
6. 修复 AE2 Utility 自身编码箭头不可用时，JEI Crafting Tree 按钮仍被错误隐藏的问题。


## v0.0.2

### English

#### Added

1. Added a spatial multi-tree workspace: complete recipe trees can be shown on one zoomable, pannable canvas and edited directly without switching the active tree first.
2. Added a Crafting Tree shortcut at the lower-left of the JEI bookmark area. It returns to the previous workspace, or guides the player to create the first tree when no workspace exists.
3. Added continuous auto-crafting from the floating material panel. It transfers ingredients through JEI into the currently open compatible menu, then takes results using the vanilla container protocol. It supports ordinary containers, AE2 terminals, and Sophisticated crafting interfaces.
4. Added stock accounting for the currently open menu, including ordinary containers, the AE2 ME client-side inventory view, and Sophisticated Storage/Backpacks.
5. Added a stable third-party API with named removable backend, inventory, and menu registrations; inventory authority groups; auto-crafting status; client events; and complete bilingual integration documentation.

#### Optimized

1. Each recipe tree now has an independent root context and view controller, including separate required materials, surplus, projects, pattern drafts, history, and planning results, preventing cross-tree state contamination.
2. Improved the placement of the “add more recipes” entry, multi-tree titles, tree boundaries, and focused-tree controls to reduce overlap and preserve the scope of merge and fit actions.
3. Aligned Sophisticated Storage, Sophisticated Core, and Sophisticated Backpacks runtime dependencies to a compatible version line.

#### Fixed

1. Fixed workspace material navigation, batch pattern encoding/upload, focused-tree material statistics, zero-input recipe collapse behavior, background planner shutdown, zoomed secondary-tree visibility, and multi-tree pointer coordinates.
2. Fixed returning from JEI recipe selection so focus and canvas position are restored to the initiating tree.
3. Fixed missing node-inspector backgrounds for secondary trees.

### 中文

#### 新增

1. 新增空间化多配方树工作区，多棵完整配方树可在同一个可缩放、可拖动的画布中同时显示，并可直接操作任意树的节点、分支与样板，无需先切换当前树。
2. 新增 JEI 书签区域左下角的配方树快捷入口；已有工作区时返回上次配方树，没有工作区时引导到 JEI 创建第一棵树。
3. 新增悬浮材料面板的连续自动合成：通过 JEI transfer 填充当前已打开的兼容菜单，再按原版容器协议取出产物；支持普通容器、AE2 终端和 Sophisticated 合成界面。
4. 新增当前打开菜单的库存统计，覆盖普通容器、AE2 ME 客户端库存视图和 Sophisticated Storage/Backpacks。
5. 新增稳定第三方 API：具名可注销后端、库存、菜单注册，库存权威分组，自动合成状态与客户端事件订阅，以及完整中英文集成文档。

#### 优化

1. 每棵配方树使用独立根上下文与视图控制器，分别保存总材料、剩余材料、项目、样板草稿、编辑历史和规划结果，避免不同树之间相互抵扣或污染状态。
2. 优化“添加更多配方”入口、多树标题、树间布局边界和聚焦树操作作用域，减少视觉遮挡并避免合并或适应操作影响无关树。
3. 对齐 Sophisticated Storage、Sophisticated Core 和 Sophisticated Backpacks 的运行时依赖版本线。

#### 修复

1. 修复多配方树工作区中的材料定位、批量样板编码/上传、聚焦树材料统计、无输入配方折叠入口、后台规划器终止、放大后的后续树可见性和多树点击坐标问题。
2. 修复从 JEI 选取下级配方返回时聚焦树和画布位置被覆盖的问题。
3. 修复第二棵及后续配方树打开节点详情时缺少面板背景的问题。

## v0.0.1 — 2026-07-23

### English

#### Added

1. Added a recursive crafting tree opened from JEI recipe layouts, with normal tree and layer-merged material views.
2. Added multi-project global production planning for raw materials, inventory allocation, byproducts, surplus, machine runs, and ordered execution checklists.
3. Added route selection, alternative-material strategies, unique-recipe expansion, existing-pattern detection, cycle detection, search, recipe/collapse memory, and Just Enough Characters pinyin-search compatibility.
4. Added a floating material panel, a scrollable node inspector, JEI recipe previews, and unified rendering for item, fluid, Mekanism chemical, and custom JEI ingredients.
5. Added optional AE2 pattern drafting for crafting, processing, stonecutting, and smithing patterns, including editable inputs, outputs, quantities, alternatives, byproducts, primary output, restore actions, input sorting, and pre-encode validation.
6. Added undo/redo for projects, trees, and pattern drafts, with modified-node visual indicators.

#### Optimized

1. Optimized very large repeated recipe trees with shared DAG branches, saturating quantity arithmetic, reused layout results, and incremental auto-expansion.
2. Added JEI lookup and recipe-ID caches, background planning cancellation/generation checks, visible-region culling, row indexes, render caches, and incremental draft synchronization.
3. Improved shared-material selection, collapse, and modified-state synchronization across tree depths; fluid and chemical rendering; and AE2 Utility coexistence.

#### Fixed

1. Fixed overlapping UI text and controls, floating-panel layering, fluid and chemical clipping/counts, shaped-recipe empty-slot layout, Mekanism chemical semantics, existing-pattern expansion blocking, pattern-draft synchronization, and node/material interactions.
2. Fixed restore, input sorting, substitution-control placement, sound, and visibility behavior.

#### Removed

1. Removed the duplicate outer border around JEI recipe previews and the extra black background behind pattern quantities.
2. Removed the requirement for JEI Crafting Tree on dedicated servers and changed material-slot quantity adjustment to direct scrolling instead of `Ctrl + scroll`.

#### Developer

1. Added `CraftingTreeBackend` for exact pattern checks, route fingerprints, reusable inputs, machine IDs, encoding, upload, and substitution controls.
2. Added structured pattern-draft models and the versioned `InventorySource` API.
3. Changed exact-pattern checks to route-sensitive normalized fingerprints and constrained client/JEI/AE2 optional dependencies to the client environment.

### 中文

#### 新增

1. 新增从 JEI 配方布局打开的递归配方树，支持普通树形视图和同层材料合并视图。
2. 新增多项目全局生产规划，可统一计算原材料、库存抵扣、副产物、剩余产物、机器运行次数和有序执行清单。
3. 新增配方路线选择、替代材料策略、唯一配方自动展开、已有样板识别、循环配方检测与可视化标记，以及搜索、配方/折叠状态记忆和 Just Enough Characters 拼音搜索兼容。
4. 新增悬浮总材料面板、可滚动的节点详情界面、JEI 原生配方布局预览，以及物品、流体、Mekanism 化学品和自定义 JEI ingredient 的统一展示。
5. 新增 AE2 可选集成：支持合成、处理、切石机和锻造台样板草稿，以及输入输出编辑、数量调整、替代项切换、副产物移除、主输出调整、恢复、输入排序和编码前验证。
6. 新增项目、配方树和样板草稿的撤销/重做能力，以及修改节点的可视化提示。

#### 优化

1. 优化九重压缩圆石等超大重复配方树：共享 DAG 分支、饱和数量运算、布局结果复用和分帧自动展开。
2. 新增 JEI 配方查询和配方 ID 缓存、后台规划取消与代次校验、可见区域裁剪、行索引、渲染缓存和样板草稿增量同步。
3. 优化跨层相同材料状态同步、流体与化学品渲染，以及 AE2 Utility 共存时的初始化流程。

#### 修复

1. 修复界面文字与控件重叠、悬浮面板层级、流体和化学品裁剪/数量、成形配方空槽布局、Mekanism 化学品语义、已有样板展开限制、样板草稿同步和节点/材料交互问题。
2. 修复恢复、输入排序和替换控制的布局、音效与显示条件。

#### 移除

1. 移除 JEI 配方布局预览的重复外层边框和样板数量文字的额外黑色底框。
2. 移除专用服务端强制安装 JEI Crafting Tree 的要求，并将材料槽数量调整改为直接滚轮操作。

#### 开发者

1. 新增 `CraftingTreeBackend`，用于精确样板判断、路线指纹、可复用输入、机器标识、编码、上传和替换控制。
2. 新增结构化样板草稿模型和带版本的 `InventorySource` API。
3. 精确样板检测改为路线敏感的规范化配方指纹，并将客户端、JEI、AE2 可选依赖限制在客户端环境。

[v0.0.1]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.1
[v0.0.2]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.2
[v0.0.3]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.3
[v0.0.4]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.4
