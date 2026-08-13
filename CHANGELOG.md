# Changelog

[v0.0.3](#v003) | [v0.0.2](#v002) | [v0.0.1](#v001--2026-07-23)

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

### 涓枃

#### 鏂板

1. 鏂板 AE2 涓?Sophisticated 鍚堟垚鐣岄潰鐨勫彲閫夋湇鍔＄鎵归噺鍚堟垚閫氶亾銆?2. 鏂板鎮诞鏉愭枡闈㈡澘鐨勫垱閫犳ā寮忚ˉ鏂欏姛鑳姐€傚鎴风鍜屾湇鍔＄閮藉畨瑁?JEICT 鏃讹紝鍙寜缂哄け鏁伴噺鍚戝綋鍓嶈彍鍗曠殑鍏煎妲戒綅琛ュ厖鐗╁搧锛涙湇鍔＄浼氶獙璇佽姹傦紝姣忔鐐瑰嚮鏈€澶氫慨鏀?64 涓Ы浣嶃€?
#### 浼樺寲

1. 浼樺寲澶ф暟閲忚嚜鍔ㄥ悎鎴愯鍒掋€?2. 浼樺寲鎮诞鏉愭枡鎶曞奖锛氭牴鎹綋鍓嶅簱瀛樻寜闇€灞曞紑涓棿浜х墿锛屽噺灏戜笉蹇呰鐨勬潗鏂欐潯鐩拰閲嶅璁＄畻銆?
#### 淇

1. 淇鏉愭枡娓呭崟鎮诞鐣岄潰鑷姩鍚堟垚鍦ㄧ簿濡欏瓨鍌ㄣ€佺簿濡欒儗鍖呭拰 ME 缁堢鏃犳硶姝ｅ父鐢熸晥鐨勯棶棰樸€?2. 淇鍒囨崲鏇夸唬鏉愭枡鍚庯紝缂栫爜鎴栦笂浼犲墠鐜版湁鏍锋澘鑽夌娌℃湁鍚屾鏂版潗鏂欑殑闂銆?3. 淇鎬昏涓悓涓€鏉愭枡鍥犲€欓€夐泦鍚堜笉鍚屻€佹垨鍚屾椂浣滀负閰嶆柟杈撳嚭涓庡彾瀛愯緭鍏ヨ€岃鎷嗗垎鎴愬鏉℃眹鎬荤殑闂銆?4. 淇宸茶蹇嗙殑涓嬬骇閰嶆柟閫夋嫨鍦ㄦ爲鐨勫叾浠栦綅缃亣鍒扮浉鍚屾潗鏂欐椂涓嶇敓鏁堢殑闂锛涢€夋嫨鐜板湪鎸夋潗鏂欑鍚嶅厹搴曘€?5. 淇 AE2 Utility 涓嬪垏鎹㈡浛浠ｆ潗鏂欏悗锛岀紪鐮?涓婁紶浠嶄娇鐢ㄥ垏鎹㈠墠鏉愭枡鐨勯棶棰樸€?6. 淇 AE2 Utility 鑷韩缂栫爜绠ご涓嶅彲鐢ㄦ椂锛孞EI Crafting Tree 鎸夐挳浠嶈閿欒闅愯棌鐨勯棶棰樸€?

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
2. Improved the placement of the 鈥渁dd more recipes鈥?entry, multi-tree titles, tree boundaries, and focused-tree controls to reduce overlap and preserve the scope of merge and fit actions.
3. Aligned Sophisticated Storage, Sophisticated Core, and Sophisticated Backpacks runtime dependencies to a compatible version line.

#### Fixed

1. Fixed workspace material navigation, batch pattern encoding/upload, focused-tree material statistics, zero-input recipe collapse behavior, background planner shutdown, zoomed secondary-tree visibility, and multi-tree pointer coordinates.
2. Fixed returning from JEI recipe selection so focus and canvas position are restored to the initiating tree.
3. Fixed missing node-inspector backgrounds for secondary trees.

### 涓枃

#### 鏂板

1. 鏂板绌洪棿鍖栧閰嶆柟鏍戝伐浣滃尯锛屽妫靛畬鏁撮厤鏂规爲鍙湪鍚屼竴涓彲缂╂斁銆佸彲鎷栧姩鐨勭敾甯冧腑鍚屾椂鏄剧ず锛屽苟鍙洿鎺ユ搷浣滀换鎰忔爲鐨勮妭鐐广€佸垎鏀笌鏍锋澘锛屾棤闇€鍏堝垏鎹㈠綋鍓嶆爲銆?2. 鏂板 JEI 涔︾鍖哄煙宸︿笅瑙掔殑閰嶆柟鏍戝揩鎹峰叆鍙ｏ紱宸叉湁宸ヤ綔鍖烘椂杩斿洖涓婃閰嶆柟鏍戯紝娌℃湁宸ヤ綔鍖烘椂寮曞鍒?JEI 鍒涘缓绗竴妫垫爲銆?3. 鏂板鎮诞鏉愭枡闈㈡澘鐨勮繛缁嚜鍔ㄥ悎鎴愶細閫氳繃 JEI transfer 濉厖褰撳墠宸叉墦寮€鐨勫吋瀹硅彍鍗曪紝鍐嶆寜鍘熺増瀹瑰櫒鍗忚鍙栧嚭浜х墿锛涙敮鎸佹櫘閫氬鍣ㄣ€丄E2 缁堢鍜?Sophisticated 鍚堟垚鐣岄潰銆?4. 鏂板褰撳墠鎵撳紑鑿滃崟鐨勫簱瀛樼粺璁★紝瑕嗙洊鏅€氬鍣ㄣ€丄E2 ME 瀹㈡埛绔簱瀛樿鍥惧拰 Sophisticated Storage/Backpacks銆?5. 鏂板绋冲畾绗笁鏂?API锛氬叿鍚嶅彲娉ㄩ攢鍚庣銆佸簱瀛樸€佽彍鍗曟敞鍐岋紝搴撳瓨鏉冨▉鍒嗙粍锛岃嚜鍔ㄥ悎鎴愮姸鎬佷笌瀹㈡埛绔簨浠惰闃咃紝浠ュ強瀹屾暣涓嫳鏂囬泦鎴愭枃妗ｃ€?
#### 浼樺寲

1. 姣忔５閰嶆柟鏍戜娇鐢ㄧ嫭绔嬫牴涓婁笅鏂囦笌瑙嗗浘鎺у埗鍣紝鍒嗗埆淇濆瓨鎬绘潗鏂欍€佸墿浣欐潗鏂欍€侀」鐩€佹牱鏉胯崏绋裤€佺紪杈戝巻鍙插拰瑙勫垝缁撴灉锛岄伩鍏嶄笉鍚屾爲涔嬮棿鐩镐簰鎶垫墸鎴栨薄鏌撶姸鎬併€?2. 浼樺寲鈥滄坊鍔犳洿澶氶厤鏂光€濆叆鍙ｃ€佸鏍戞爣棰樸€佹爲闂村竷灞€杈圭晫鍜岃仛鐒︽爲鎿嶄綔浣滅敤鍩燂紝鍑忓皯瑙嗚閬尅骞堕伩鍏嶅悎骞舵垨閫傚簲鎿嶄綔褰卞搷鏃犲叧鏍戙€?3. 瀵归綈 Sophisticated Storage銆丼ophisticated Core 鍜?Sophisticated Backpacks 鐨勮繍琛屾椂渚濊禆鐗堟湰绾裤€?
#### 淇

1. 淇澶氶厤鏂规爲宸ヤ綔鍖轰腑鐨勬潗鏂欏畾浣嶃€佹壒閲忔牱鏉跨紪鐮?涓婁紶銆佽仛鐒︽爲鏉愭枡缁熻銆佹棤杈撳叆閰嶆柟鎶樺彔鍏ュ彛銆佸悗鍙拌鍒掑櫒缁堟銆佹斁澶у悗鐨勫悗缁爲鍙鎬у拰澶氭爲鐐瑰嚮鍧愭爣闂銆?2. 淇浠?JEI 閫夊彇涓嬬骇閰嶆柟杩斿洖鏃惰仛鐒︽爲鍜岀敾甯冧綅缃瑕嗙洊鐨勯棶棰樸€?3. 淇绗簩妫靛強鍚庣画閰嶆柟鏍戞墦寮€鑺傜偣璇︽儏鏃剁己灏戦潰鏉胯儗鏅殑闂銆?
## v0.0.1 鈥?2026-07-23

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

### 涓枃

#### 鏂板

1. 鏂板浠?JEI 閰嶆柟甯冨眬鎵撳紑鐨勯€掑綊閰嶆柟鏍戯紝鏀寔鏅€氭爲褰㈣鍥惧拰鍚屽眰鏉愭枡鍚堝苟瑙嗗浘銆?2. 鏂板澶氶」鐩叏灞€鐢熶骇瑙勫垝锛屽彲缁熶竴璁＄畻鍘熸潗鏂欍€佸簱瀛樻姷鎵ｃ€佸壇浜х墿銆佸墿浣欎骇鐗┿€佹満鍣ㄨ繍琛屾鏁板拰鏈夊簭鎵ц娓呭崟銆?3. 鏂板閰嶆柟璺嚎閫夋嫨銆佹浛浠ｆ潗鏂欑瓥鐣ャ€佸敮涓€閰嶆柟鑷姩灞曞紑銆佸凡鏈夋牱鏉胯瘑鍒€佸惊鐜厤鏂规娴嬩笌鍙鍖栨爣璁帮紝浠ュ強鎼滅储銆侀厤鏂?鎶樺彔鐘舵€佽蹇嗗拰 Just Enough Characters 鎷奸煶鎼滅储鍏煎銆?4. 鏂板鎮诞鎬绘潗鏂欓潰鏉裤€佸彲婊氬姩鐨勮妭鐐硅鎯呯晫闈€丣EI 鍘熺敓閰嶆柟甯冨眬棰勮锛屼互鍙婄墿鍝併€佹祦浣撱€丮ekanism 鍖栧鍝佸拰鑷畾涔?JEI ingredient 鐨勭粺涓€灞曠ず銆?5. 鏂板 AE2 鍙€夐泦鎴愶細鏀寔鍚堟垚銆佸鐞嗐€佸垏鐭虫満鍜岄敾閫犲彴鏍锋澘鑽夌锛屼互鍙婅緭鍏ヨ緭鍑虹紪杈戙€佹暟閲忚皟鏁淬€佹浛浠ｉ」鍒囨崲銆佸壇浜х墿绉婚櫎銆佷富杈撳嚭璋冩暣銆佹仮澶嶃€佽緭鍏ユ帓搴忓拰缂栫爜鍓嶉獙璇併€?6. 鏂板椤圭洰銆侀厤鏂规爲鍜屾牱鏉胯崏绋跨殑鎾ら攢/閲嶅仛鑳藉姏锛屼互鍙婁慨鏀硅妭鐐圭殑鍙鍖栨彁绀恒€?
#### 浼樺寲

1. 浼樺寲涔濋噸鍘嬬缉鍦嗙煶绛夎秴澶ч噸澶嶉厤鏂规爲锛氬叡浜?DAG 鍒嗘敮銆侀ケ鍜屾暟閲忚繍绠椼€佸竷灞€缁撴灉澶嶇敤鍜屽垎甯ц嚜鍔ㄥ睍寮€銆?2. 鏂板 JEI 閰嶆柟鏌ヨ鍜岄厤鏂?ID 缂撳瓨銆佸悗鍙拌鍒掑彇娑堜笌浠ｆ鏍￠獙銆佸彲瑙佸尯鍩熻鍓€佽绱㈠紩銆佹覆鏌撶紦瀛樺拰鏍锋澘鑽夌澧為噺鍚屾銆?3. 浼樺寲璺ㄥ眰鐩稿悓鏉愭枡鐘舵€佸悓姝ャ€佹祦浣撲笌鍖栧鍝佹覆鏌擄紝浠ュ強 AE2 Utility 鍏卞瓨鏃剁殑鍒濆鍖栨祦绋嬨€?
#### 淇

1. 淇鐣岄潰鏂囧瓧涓庢帶浠堕噸鍙犮€佹偓娴潰鏉垮眰绾с€佹祦浣撳拰鍖栧鍝佽鍓?鏁伴噺銆佹垚褰㈤厤鏂圭┖妲藉竷灞€銆丮ekanism 鍖栧鍝佽涔夈€佸凡鏈夋牱鏉垮睍寮€闄愬埗銆佹牱鏉胯崏绋垮悓姝ュ拰鑺傜偣/鏉愭枡浜や簰闂銆?2. 淇鎭㈠銆佽緭鍏ユ帓搴忓拰鏇挎崲鎺у埗鐨勫竷灞€銆侀煶鏁堜笌鏄剧ず鏉′欢銆?
#### 绉婚櫎

1. 绉婚櫎 JEI 閰嶆柟甯冨眬棰勮鐨勯噸澶嶅灞傝竟妗嗗拰鏍锋澘鏁伴噺鏂囧瓧鐨勯澶栭粦鑹插簳妗嗐€?2. 绉婚櫎涓撶敤鏈嶅姟绔己鍒跺畨瑁?JEI Crafting Tree 鐨勮姹傦紝骞跺皢鏉愭枡妲芥暟閲忚皟鏁存敼涓虹洿鎺ユ粴杞搷浣溿€?
#### 寮€鍙戣€?
1. 鏂板 `CraftingTreeBackend`锛岀敤浜庣簿纭牱鏉垮垽鏂€佽矾绾挎寚绾广€佸彲澶嶇敤杈撳叆銆佹満鍣ㄦ爣璇嗐€佺紪鐮併€佷笂浼犲拰鏇挎崲鎺у埗銆?2. 鏂板缁撴瀯鍖栨牱鏉胯崏绋挎ā鍨嬪拰甯︾増鏈殑 `InventorySource` API銆?3. 绮剧‘鏍锋澘妫€娴嬫敼涓鸿矾绾挎晱鎰熺殑瑙勮寖鍖栭厤鏂规寚绾癸紝骞跺皢瀹㈡埛绔€丣EI銆丄E2 鍙€変緷璧栭檺鍒跺湪瀹㈡埛绔幆澧冦€?
[v0.0.1]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.1
[v0.0.2]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.2
[v0.0.3]: https://github.com/lhy512103/JEI-Crafting-Tree/releases/tag/v0.0.3