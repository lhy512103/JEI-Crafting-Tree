# Changelog

[v1.0.0](#v100) | [v0.0.4](#v004) | [v0.0.3](#v003) | [v0.0.2](#v002) | [v0.0.1](#v001--2026-07-23)

## v1.0.0

### English

1. Optimized: auto-crafting stops with a clear reason instead of retrying silently.
2. Added: Shift+click on Craft crafts a single batch.
3. Added: floating panel status line, red/yellow shortage tint and missing-material tooltip.
4. Optimized: creative Refill fills everything in one click and supports the player inventory.
5. Fixed: creative Refill ignoring stack upgrades of Sophisticated containers.
6. Fixed: missing materials reported as "unsupported screen".
7. Added: API `StopReason` values `MISSING_ITEMS`, `NO_HANDLER`, `NO_CONTAINER`, `REJECTED`.
8. Optimized: the material requirements panel uses a bookmark-style slot flow in a floating overlay.
9. Fixed: creative refill over-supplying materials; demand is now merged by material type.
10. Optimized: multi-candidate inputs default to AE2 encoding order: already-craftable/pattern first, then undamaged, then highest stock.
11. Optimized: alternative-material picker is a draggable/resizable floating panel with a search box.

### 中文

1. 优化：自动合成失败时明确停止并提示原因，不再无声重试。
2. 新增：合成按钮支持 Shift+点击只合成一批。
3. 新增：悬浮面板状态栏、缺料红/黄标记及缺料清单提示。
4. 优化：创造补料一次补完，并支持补充到玩家物品栏。
5. 修复：创造补料不识别精妙存储/背包的堆叠升级。
6. 修复：材料不足被误报为”界面不支持”。
7. 新增：API `StopReason` 增加 `MISSING_ITEMS`、`NO_HANDLER`、`NO_CONTAINER`、`REJECTED`。
8. 优化：材料需求面板改为书签式槽位流并采用悬浮面板。
9. 修复：创造补充材料过多的bug，现在按材料种类合并需求量。
10. 优化：多候选输入默认按 AE 编码规则选择：已有样板优先，其次完好，再按存量。
11. 优化：候选材料选择改为可拖拽缩放的悬浮面板，并带搜索框。

## v0.0.4

### English

1. Fixed: batch-count scrolling in the multi-tree workspace.

### 中文

1. 修复：多配方树工作区无法用滚轮调节批次。

## v0.0.3

### English

1. Added: optional server-side batch crafting for AE2 and Sophisticated interfaces.
2. Added: creative-mode material refill in the floating panel.
3. Optimized: large-quantity auto-crafting planning.
4. Optimized: floating material projection expands intermediates only as needed.
5. Fixed: auto-crafting failing in Sophisticated Storage/Backpacks and ME terminals.
6. Fixed: alternative-material changes not synced into pattern drafts before encoding.
7. Fixed: the same material split into several aggregates in the overview.
8. Fixed: remembered child-recipe selections not applied to the same material elsewhere.
9. Fixed: AE2 Utility encoding/upload using the previously selected material.
10. Fixed: AE2 Utility integration wrongly hiding the JEI Crafting Tree button.

### 中文

1. 新增：AE2 与 Sophisticated 界面的可选服务端批量合成。
2. 新增：悬浮材料面板的创造模式补料。
3. 优化：大数量自动合成规划。
4. 优化：悬浮材料投影按需展开中间产物。
5. 修复：自动合成在精妙存储、精妙背包和 ME 终端中失效。
6. 修复：切换替代材料后，编码前样板草稿未同步。
7. 修复：总览中同一材料被拆成多条汇总。
8. 修复：已记忆的下级配方选择在其他位置的相同材料上不生效。
9. 修复：AE2 Utility 编码/上传仍使用切换前的材料。
10. 修复：AE2 Utility 集成错误隐藏 JEI Crafting Tree 按钮。

## v0.0.2

### English

1. Added: spatial multi-tree workspace on one zoomable, pannable canvas.
2. Added: Crafting Tree shortcut at the lower-left of the JEI bookmark area.
3. Added: continuous auto-crafting from the floating panel for containers, AE2 terminals and Sophisticated interfaces.
4. Added: stock accounting for the open menu (containers, AE2 ME, Sophisticated).
5. Added: stable third-party API with bilingual integration docs.
6. Optimized: each tree has its own context, materials, drafts and history.
7. Optimized: multi-tree layout, titles, boundaries and focused-tree controls.
8. Optimized: aligned Sophisticated runtime dependency versions.
9. Fixed: multi-tree material navigation, batch encoding/upload, statistics and pointer coordinates.
10. Fixed: focus and canvas position lost when returning from JEI recipe selection.
11. Fixed: missing node-inspector background for secondary trees.

### 中文

1. 新增：空间化多配方树工作区，同一画布可缩放、拖动。
2. 新增：JEI 书签区域左下角的配方树快捷入口。
3. 新增：悬浮面板连续自动合成，支持普通容器、AE2 终端和 Sophisticated 界面。
4. 新增：当前打开菜单的库存统计（普通容器、AE2 ME、Sophisticated）。
5. 新增：稳定的第三方 API 及中英文集成文档。
6. 优化：每棵树独立的上下文、材料、样板草稿和历史。
7. 优化：多树布局、标题、边界和聚焦树操作。
8. 优化：对齐 Sophisticated 系列运行时依赖版本。
9. 修复：多树工作区的材料定位、批量编码/上传、统计和点击坐标。
10. 修复：从 JEI 选取配方返回后聚焦树和画布位置丢失。
11. 修复：后续配方树的节点详情缺少面板背景。

## v0.0.1 — 2026-07-23

### English

1. Added: recursive crafting tree opened from JEI, with tree and layer-merged views.
2. Added: multi-project global planning with inventory allocation, byproducts and execution checklists.
3. Added: route selection, alternative strategies, auto-expansion, existing-pattern and cycle detection, search, memory, and pinyin-search compatibility.
4. Added: floating material panel, node inspector, JEI recipe previews, and unified item/fluid/chemical rendering.
5. Added: optional AE2 pattern drafting for crafting, processing, stonecutting and smithing.
6. Added: undo/redo for projects, trees and pattern drafts.
7. Added: `CraftingTreeBackend` and versioned `InventorySource` API with structured pattern drafts.
8. Optimized: very large repeated trees (shared DAG, saturating arithmetic, incremental expansion).
9. Optimized: JEI lookup caches, background planning, culling and render caches.
10. Optimized: shared-material sync across depths and AE2 Utility coexistence.
11. Fixed: overlapping UI, floating-panel layering, fluid/chemical clipping, and draft sync issues.
12. Fixed: restore, input sorting and substitution-control behavior.
13. Removed: duplicate JEI preview border and extra black background behind quantities.
14. Removed: JEICT requirement on dedicated servers; `Ctrl + scroll` for slot quantities.

### 中文

1. 新增：从 JEI 打开的递归配方树，支持树形与同层合并视图。
2. 新增：多项目全局规划，含库存抵扣、副产物与执行清单。
3. 新增：路线选择、替代材料策略、自动展开、已有样板与循环检测、搜索、状态记忆及拼音搜索兼容。
4. 新增：悬浮材料面板、节点详情、JEI 配方预览，统一展示物品/流体/化学品。
5. 新增：AE2 可选样板草稿，支持合成、处理、切石机和锻造台。
6. 新增：项目、配方树和样板草稿的撤销/重做。
7. 新增：`CraftingTreeBackend` 与带版本的 `InventorySource` API，及结构化样板草稿。
8. 优化：超大重复配方树（共享 DAG、饱和运算、分帧展开）。
9. 优化：JEI 查询缓存、后台规划、可见区域裁剪和渲染缓存。
10. 优化：跨层相同材料同步及 AE2 Utility 共存。
11. 修复：界面重叠、悬浮面板层级、流体/化学品裁剪及草稿同步问题。
12. 修复：恢复、输入排序和替换控制的行为。
13. 移除：JEI 配方预览的重复边框和数量文字的黑色底框。
14. 移除：专用服务端必须安装 JEICT 的要求；材料槽数量调整不再需要 `Ctrl + 滚轮`。

[v0.0.1]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.1
[v0.0.2]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.2
[v0.0.3]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.3
[v0.0.4]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.4
