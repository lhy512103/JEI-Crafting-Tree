package com.lhy.jeict.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.NavigableMap;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.lhy.jeict.api.CraftingTreeBackend;
import com.lhy.jeict.api.CraftingTreeBackends;
import com.lhy.jeict.debug.RecipeTreePerfDebug;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.recipe_tree.RequestedIngredient;
import com.lhy.jeict.util.GenericIngredientUtil;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RecipeTreeOverviewScreen extends Screen implements RecipeTreeJeiTransferTarget {
    private static final int NODE_HEIGHT = 28;
    private static final int NODE_MAIN_SIZE = 28;
    private static final int NODE_PART_GAP = 2;
    private static final int NODE_PART_PADDING = 2;
    private static final int LEVEL_GAP = 46;
    private static final int LEAF_GAP = 6;
    private static final int AUTO_EXPAND_STEPS_PER_TICK = 32;
    private static final int VISIBILITY_MARGIN = 32;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 34;
    private static final double INITIAL_MIN_ZOOM = 0.7D;
    private static final double INITIAL_MAX_ZOOM = 1.0D;
    private static final int MACHINE_SLOT_SIZE = 18;
    /** 与样板编码终端/JEI 开关同源图标（8×8 纹理缩放绘制） */
    private static final int SUBSTITUTION_ICON_SRC = 8;
    private static final int SUBSTITUTION_ICON_DST = 16;
    private static final int TOP_MATERIALS_OFFSET = 100;
    private static final int INSPECTOR_WIDTH = 216;
    private static final int SETTINGS_WIDTH = 148;
    private static final int SETTINGS_COLLAPSED_WIDTH = 34;
    private static final int BATCH_TEXT_GAP = 3;

    private final RecipeTreeRootContext context;
    private final Screen returnScreen;
    private final List<PositionedNode> positionedNodes = new ArrayList<>();
    private final List<PositionedNode> visiblePositionedNodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final NavigableMap<Integer, List<PositionedNode>> positionedNodeRows = new TreeMap<>();
    private final NavigableMap<Integer, List<Edge>> edgeRows = new TreeMap<>();
    private final Map<LayerRow, LayerRowRenderCache> mergedRowRenderCaches = new IdentityHashMap<>();
    private final Map<GraphNode, Integer> graphSubtreeWidthCache = new IdentityHashMap<>();
    private final Map<RecipeTreeRecipeViewModel, Boolean> existingPatternRecipeCache = new IdentityHashMap<>();
    private final Map<String, Boolean> existingPatternMaterialCache = new HashMap<>();
    private final Map<String, List<UnresolvedInputSlot>> unresolvedInputsBySignature = new HashMap<>();
    private final Map<RecipeTreeInputViewModel, CachedInputSignature> inputSignatureCache = new IdentityHashMap<>();
    private final Map<RecipeTreeInputViewModel, CachedLayerMaterialKey> leafLayerKeyCache = new IdentityHashMap<>();
    private final Map<RecipeTreeNodeViewModel, LayerMaterialKey> recipeLayerKeyCache = new IdentityHashMap<>();
    private final Map<ITypedIngredient<?>, String> typedIngredientSignatureCache = new IdentityHashMap<>();
    private final List<TopMaterialBounds> topMaterialBounds = new ArrayList<>();
    private final List<TopMaterialBounds> surplusMaterialBounds = new ArrayList<>();
    private final List<GenericTopMaterialBounds> genericTopMaterialBounds = new ArrayList<>();
    private final List<AlternativeButtonBounds> alternativeButtonBounds = new ArrayList<>();
    private final List<AlternativeOptionBounds> alternativeOptionBounds = new ArrayList<>();
    private final List<LayerMaterialBounds> layerMaterialBounds = new ArrayList<>();
    private @Nullable TopMaterialsPinButtonBounds topMaterialsPinButtonBounds;
    private List<RequestedIngredient> topMaterials = List.of();
    private List<RequestedIngredient> surplusMaterials = List.of();
    /**
     * Render-time projection for the top-material strip. Inventory lookups and
     * label width calculation are kept out of the frame loop; this list is
     * refreshed when the layout, theme, or player inventory changes.
     */
    private List<TopMaterialRenderData> topMaterialRenderData = List.of();
    private List<TopMaterialRenderData> surplusMaterialRenderData = List.of();
    private List<GenericTopMaterialRenderData> genericTopMaterialRenderData = List.of();
    private boolean topMaterialRenderCacheDirty = true;
    private boolean topMaterialPatternHintsDirty = true;
    private int topMaterialInventoryVersion = Integer.MIN_VALUE;
    private @Nullable Inventory topMaterialInventory;
    private int cachedMissingMaterialCount;
    private List<LayerRow> mergedLayerRows = List.of();
    /** Recomputed only when the layer projection changes; render() reads this on every frame. */
    private int cachedMergedContentWidth = 1;
    private int cachedMergedAnchorOffset;

    private GraphNode rootNode;
    private Button backButton;
    private Button toggleExistingPatternButton;
    private Button autoUniqueRecipeButton;
    private Button memoryReadingButton;
    private Button autoMergeButton;
    private Button styleButton;
    private Button encodeButton;
    private Button uploadButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button fitViewButton;
    private Button settingsButton;
    private @Nullable BatchBadgeBounds batchBadgeBounds;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private double visibleLogicalMinX;
    private double visibleLogicalMaxX;
    private double visibleLogicalMinY;
    private double visibleLogicalMaxY;
    private boolean initializedPan;
    private int alternativeScroll;
    private int toolbarLeft;
    private boolean settingsOpen;
    private @Nullable PositionedNode selectedNode;
    private @Nullable LayerMaterial selectedLayerMaterial;
    private int cachedRequiredPatternCount;
    private Component cachedRequiredPatternsTitleLine = Component.empty();
    private boolean autoMergeSameMaterials = true;
    /** 关闭后不演算树上用量、所需样板数，也不查询 ME 中是否已有样板（减轻卡顿） */
    private boolean computeRecipeQuantities = true;
    private Button computeQuantitiesButton;
    /** 开启时自动展开「JEI 中仅有一条可编码配方」的未解析分支；若「已有样板:禁」则跳过网络中已有样板的分支（见 shouldBlockExpansion） */
    private boolean autoExpandUniqueEncodableRecipe = false;
    private boolean suppressAutoExpandUniqueRecipePass;
    private boolean autoExpandUniqueSearchPending;
    private boolean readRememberedSelections;
    private boolean rememberedSelectionsDirty = true;
    private int batchCount = 1;
    private final Set<String> manuallyCollapsedSignatures = new HashSet<>();
    private final Map<String, Optional<RecipeTreeRecipeViewModel>> autoExpandUniqueCandidateCache = new HashMap<>();
    private @Nullable PendingJeiSelection pendingJeiSelection;
    private @Nullable PendingAlternativeSelection pendingAlternativeSelection;
    private int lastRenderedNodeCount;
    private int lastRenderedEdgeCount;
    private int lastRenderedLayerCount;
    private int lastRenderedLayerMaterialCount;
    private int lastRenderedTopMaterialCount;
    private @Nullable MergedBuildStats lastMergedBuildStats;

    public RecipeTreeOverviewScreen(RecipeTreeRootContext context, Screen returnScreen) {
        super(Component.translatable("gui.jeict.recipe_tree.overview_title"));
        this.context = context;
        this.returnScreen = returnScreen;
        this.readRememberedSelections = RecipeTreeClientMemory.isMemoryReadingEnabled();
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = chromeButton(8, 8, 50, 20,
                Component.translatable("gui.jeict.recipe_tree.back"), btn -> onClose());
        this.toggleExistingPatternButton = chromeButton(this.width - 220, 8, 86, 18, Component.empty(),
                btn -> {
                    context.setDisableExistingPatternExpansion(!context.disableExistingPatternExpansion());
                    if (context.disableExistingPatternExpansion()) {
                        collapseExpandedExistingPatternNodes(context.root());
                    }
                    syncToggleExistingPatternButton();
                    rebuildLayout();
                });
        this.autoUniqueRecipeButton = chromeButton(this.width - 296, 8, 72, 18, Component.empty(),
                btn -> {
                    autoExpandUniqueEncodableRecipe = !autoExpandUniqueEncodableRecipe;
                    markAutoExpandUniqueDirty();
                    syncAutoUniqueRecipeButton();
                    rebuildLayout();
                });
        this.memoryReadingButton = chromeButton(this.width - 372, 8, 72, 18, Component.empty(),
                btn -> {
                    readRememberedSelections = !readRememberedSelections;
                    rememberedSelectionsDirty = readRememberedSelections;
                    RecipeTreeClientMemory.setMemoryReadingEnabled(readRememberedSelections);
                    syncMemoryReadingButton();
                    rebuildLayout();
                });
        this.computeQuantitiesButton = chromeButton(this.width - 372, 8, 64, 18, Component.empty(),
                btn -> {
                    computeRecipeQuantities = !computeRecipeQuantities;
                    syncComputeQuantitiesButton();
                    refreshRenderedProjection();
                });
        this.autoMergeButton = chromeButton(this.width - 148, 8, 68, 18, Component.empty(),
                btn -> {
                    autoMergeSameMaterials = !autoMergeSameMaterials;
                    syncAutoMergeButton();
                    rebuildLayout();
                });
        this.styleButton = chromeButton(this.width - 448, 8, 72, 18, Component.empty(),
                btn -> {
                    RecipeTreeTheme.toggle();
                    topMaterialRenderCacheDirty = true;
                    syncStyleButton();
                });
        this.zoomOutButton = chromeButton(this.width - 180, 8, 22, 20, Component.literal("−"), btn -> zoomAtCenter(-0.1D));
        this.zoomInButton = chromeButton(this.width - 106, 8, 22, 20, Component.literal("+"), btn -> zoomAtCenter(0.1D));
        this.fitViewButton = chromeButton(this.width - 80, 8, 42, 20,
                Component.translatable("gui.jeict.recipe_tree.overview_fit"), btn -> fitCurrentView());
        this.settingsButton = chromeButton(this.width - 34, 8, 26, 20, Component.literal("⋮"), btn -> {
            settingsOpen = !settingsOpen;
            updateSelectionButtons();
        });
        this.encodeButton = chromeButton(this.width / 2 - 74, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.encode"), btn -> encodePatterns());
        this.uploadButton = chromeButton(this.width / 2 + 4, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.upload"), btn -> uploadPatterns());

        this.addRenderableWidget(backButton);
        this.addRenderableWidget(computeQuantitiesButton);
        this.addRenderableWidget(styleButton);
        this.addRenderableWidget(toggleExistingPatternButton);
        this.addRenderableWidget(autoUniqueRecipeButton);
        this.addRenderableWidget(memoryReadingButton);
        this.addRenderableWidget(autoMergeButton);
        this.addRenderableWidget(zoomOutButton);
        this.addRenderableWidget(zoomInButton);
        this.addRenderableWidget(fitViewButton);
        this.addRenderableWidget(settingsButton);
        this.addRenderableWidget(encodeButton);
        this.addRenderableWidget(uploadButton);
        syncComputeQuantitiesButton();
        syncStyleButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncMemoryReadingButton();
        syncAutoMergeButton();
        updateSelectionButtons();
        rebuildLayout();
    }


    @Override
    public void tick() {
        super.tick();
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend != null && backend.pollExistingPatternCachesStale()) {
            refreshCraftableDependentCaches();
        }
        processAutoExpandUniqueRecipeSteps(AUTO_EXPAND_STEPS_PER_TICK);
        refreshTopMaterialRenderCacheIfNeeded();
    }

    private void rebuildLayout() {
        selectedNode = null;
        selectedLayerMaterial = null;
        long startedAt = RecipeTreePerfDebug.begin();
        rebuildLayoutStructureCore();
        refreshLayoutDerivedCaches();
        markAutoExpandUniqueDirty();
        RecipeTreePerfDebug.logPhase("rebuild_layout", startedAt,
                "merge={} qty={} autoUnique={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, computeRecipeQuantities, autoExpandUniqueEncodableRecipe,
                positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    /**
     * 数量/提示类开关变化时，只重建渲染投影，不再次跑自动展开流程。
     */
    private void refreshRenderedProjection() {
        selectedNode = null;
        selectedLayerMaterial = null;
        long startedAt = RecipeTreePerfDebug.begin();
        rebuildLayoutStructureCore();
        refreshLayoutDerivedCaches();
        RecipeTreePerfDebug.logPhase("refresh_projection", startedAt,
                "merge={} qty={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, computeRecipeQuantities,
                positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    private void refreshLayoutDerivedCaches() {
        long startedAt = RecipeTreePerfDebug.begin();
        List<RecipeTreeRecipeViewModel> selectedRecipes = context.collectSelectedRecipes();
        if (computeRecipeQuantities) {
            cachedRequiredPatternCount = computeRequiredPatternCountUncached(selectedRecipes);
        }
        rebuildRequiredPatternsTitleComponent();
        RecipeTreePerfDebug.logPhase("refresh_derived", startedAt,
                "qty={} requiredPatterns={} selectedRecipes={}",
                computeRecipeQuantities, cachedRequiredPatternCount, selectedRecipes.size());
    }

    private void rebuildRequiredPatternsTitleComponent() {
        if (!computeRecipeQuantities) {
            cachedRequiredPatternsTitleLine = Component.translatable("gui.jeict.recipe_tree.overview_required_patterns_skipped");
        } else {
            cachedRequiredPatternsTitleLine =
                    Component.translatable("gui.jeict.recipe_tree.overview_required_patterns", cachedRequiredPatternCount);
        }
    }

    private void refreshCraftableDependentCaches() {
        long startedAt = RecipeTreePerfDebug.begin();
        existingPatternRecipeCache.clear();
        existingPatternMaterialCache.clear();
        if (!computeRecipeQuantities) {
            RecipeTreePerfDebug.logPhase("refresh_craftable_dependent_skip", startedAt, "qty=false");
            return;
        }
        topMaterialRenderCacheDirty = true;
        topMaterialPatternHintsDirty = true;
        cachedRequiredPatternCount = computeRequiredPatternCountUncached(context.collectSelectedRecipes());
        rebuildRequiredPatternsTitleComponent();
        if (!autoMergeSameMaterials || mergedLayerRows.isEmpty()) {
            RecipeTreePerfDebug.logPhase("refresh_craftable_dependent", startedAt,
                    "merge={} rows={} requiredPatterns={}",
                    autoMergeSameMaterials, mergedLayerRows.size(), cachedRequiredPatternCount);
            return;
        }
        List<LayerRow> rebuilt = new ArrayList<>(mergedLayerRows.size());
        int changedHints = 0;
        for (LayerRow row : mergedLayerRows) {
            List<LayerMaterial> mats = new ArrayList<>(row.materials().size());
            for (LayerMaterial m : row.materials()) {
                boolean hint = computeLayerMaterialShowsPatternHint(m);
                if (m.showsPatternHint() != hint) {
                    changedHints++;
                    mats.add(m.withShowsPatternHint(hint));
                } else {
                    mats.add(m);
                }
            }
            rebuilt.add(new LayerRow(row.depth(), List.copyOf(mats)));
        }
        mergedLayerRows = List.copyOf(rebuilt);
        refreshMergedContentWidth();
        RecipeTreePerfDebug.logPhase("refresh_craftable_dependent", startedAt,
                "merge={} rows={} changedHints={} requiredPatterns={}",
                autoMergeSameMaterials, mergedLayerRows.size(), changedHints, cachedRequiredPatternCount);
    }

    private void rebuildLayoutStructureCore() {
        long startedAt = RecipeTreePerfDebug.begin();
        long structureBuildStartedAt = RecipeTreePerfDebug.begin();
        if (readRememberedSelections && rememberedSelectionsDirty) {
            autoApplyRememberedChildren(context.root());
            rememberedSelectionsDirty = false;
        }
        graphSubtreeWidthCache.clear();
        if (autoMergeSameMaterials) {
            this.rootNode = null;
            this.mergedLayerRows = buildMergedLayerRows(context.root(), batchCount);
            refreshMergedContentWidth();
            if (lastMergedBuildStats != null) {
                RecipeTreePerfDebug.logPhase("build_merged_rows", structureBuildStartedAt,
                        "calls={} recipeNodeAdds={} leafAdds={} aggregatedChildLinks={} rows={} rowMaterials={}",
                        lastMergedBuildStats.collectCalls, lastMergedBuildStats.recipeNodeAdds,
                        lastMergedBuildStats.leafAdds, lastMergedBuildStats.aggregatedChildLinks,
                        lastMergedBuildStats.layerRows, lastMergedBuildStats.layerMaterials);
            }
        } else {
            this.rootNode = buildGraph(context.root(), batchCount);
            this.mergedLayerRows = List.of();
            this.cachedMergedContentWidth = 1;
            this.cachedMergedAnchorOffset = 0;
            lastMergedBuildStats = null;
            RecipeTreePerfDebug.logPhase("build_graph_tree", structureBuildStartedAt,
                    "nodes={} edges={}", positionedNodes.size(), edges.size());
        }
        long topMaterialsStartedAt = RecipeTreePerfDebug.begin();
        this.genericTopMaterialRenderData = collectGenericTopMaterialRenderData();
        this.surplusMaterials = computeRecipeQuantities ? context.collectSurplusIngredients(batchCount) : List.of();
        topMaterialRenderCacheDirty = true;
        topMaterialPatternHintsDirty = true;
        refreshTopMaterialRenderCacheIfNeeded();
        RecipeTreePerfDebug.logPhase("collect_top_materials", topMaterialsStartedAt,
                "count={}", topMaterials.size());
        positionedNodes.clear();
        visiblePositionedNodes.clear();
        edges.clear();
        positionedNodeRows.clear();
        edgeRows.clear();
        mergedRowRenderCaches.clear();
        layerMaterialBounds.clear();
        if (autoMergeSameMaterials) {
            if (!initializedPan) {
                int contentWidth = computeMergedLayerContentWidth();
                int contentHeight = computeMergedLayerContentHeight();
                double left = 36.0D;
                double top = 42.0D + TOP_MATERIALS_OFFSET - NODE_HEIGHT - 28.0D;
                fitInitialView(left, top, left + contentWidth,
                        42.0D + TOP_MATERIALS_OFFSET + contentHeight);
                initializedPan = true;
            }
        } else {
            PositionedNode root = layoutNode(rootNode, 0, 36);
            if (!initializedPan) {
                fitInitialTreeView(root);
                initializedPan = true;
            }
        }
        finalizeSpatialIndex();
        RecipeTreePerfDebug.logPhase("rebuild_structure", startedAt,
                "merge={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    /**
     * 按签名合并未解析输入，尝试套用唯一且当前可编码的 JEI 配方。「已有样板:禁」时仍运行，但若该材料在 ME 已有样板则跳过（shouldBlockExpansion）。
     */
    private boolean processAutoExpandUniqueRecipeSteps(int maxSteps) {
        long startedAt = RecipeTreePerfDebug.begin();
        if (suppressAutoExpandUniqueRecipePass || !autoExpandUniqueEncodableRecipe || !autoExpandUniqueSearchPending) {
            if (RecipeTreePerfDebug.isEnabled()) {
                RecipeTreePerfDebug.logPhase("auto_expand_skip", startedAt,
                        "suppressed={} enabled={} pending={}",
                        suppressAutoExpandUniqueRecipePass, autoExpandUniqueEncodableRecipe, autoExpandUniqueSearchPending);
            }
            return false;
        }
        suppressAutoExpandUniqueRecipePass = true;
        try {
            boolean mutated = false;
            int applied = 0;
            for (int step = 0; step < Math.max(1, maxSteps); step++) {
                int appliedThisPass = tryAutoExpandUniqueEncodableRecipeSinglePass();
                if (appliedThisPass <= 0) {
                    break;
                }
                mutated = true;
                applied += appliedThisPass;
            }
            if (mutated) {
                rebuildLayoutStructureCore();
                refreshLayoutDerivedCaches();
            }
            autoExpandUniqueSearchPending = mutated;
            if (RecipeTreePerfDebug.isEnabled()) {
                RecipeTreePerfDebug.logPhase("auto_expand", startedAt,
                        "steps={} mutated={} nodes={} edges={} rows={}",
                        applied, mutated, positionedNodes.size(), edges.size(), mergedLayerRows.size());
            }
            return mutated;
        } finally {
            suppressAutoExpandUniqueRecipePass = false;
        }
    }

    private int tryAutoExpandUniqueEncodableRecipeSinglePass() {
        Map<String, List<UnresolvedInputSlot>> grouped = new LinkedHashMap<>();
        Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache = new HashMap<>();
        collectUnresolvedInputsGrouped(context.root(), grouped);
        int applied = 0;
        for (List<UnresolvedInputSlot> group : grouped.values()) {
            if (group.isEmpty()) {
                continue;
            }
            if (group.stream().anyMatch(slot -> slot.input().hasAlternativeChoices())) {
                continue;
            }
            MergedLeaf leaf = mergedLeafFromUnresolvedGroup(group);
            if (leaf == null || hasManuallyCollapsedInput(leaf)) {
                continue;
            }
            if (shouldBlockExpansion(leaf)) {
                continue;
            }
            ITypedIngredient<?> focus = getJeiSelectionIngredient(leaf.representative());
            if (focus == null || JeiCraftingTreePlugin.getJeiRuntime() == null) {
                continue;
            }
            Optional<RecipeTreeRecipeViewModel> chosen = resolveUniqueEncodableRecipe(signatureOf(focus), focus);
            if (chosen.isEmpty()) {
                continue;
            }
            if (wouldCauseRecursiveLeafExpansion(leaf, chosen.get())) {
                continue;
            }
            applyLeafSelection(leaf, chosen.get(), rememberedCache);
            applied++;
        }
        return applied;
    }

    private void collectUnresolvedInputsGrouped(RecipeTreeNodeViewModel node, Map<String, List<UnresolvedInputSlot>> grouped) {
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                grouped.computeIfAbsent(signatureOf(input), k -> new ArrayList<>()).add(new UnresolvedInputSlot(input, node));
            } else {
                collectUnresolvedInputsGrouped(child, grouped);
            }
        }
    }

    private @Nullable MergedLeaf mergedLeafFromUnresolvedGroup(List<UnresolvedInputSlot> group) {
        if (group.isEmpty()) {
            return null;
        }
        RecipeTreeInputViewModel representative = group.getFirst().input();
        RecipeTreeNodeViewModel anchorParent = group.getFirst().parentNode();
        List<RecipeTreeInputViewModel> members = new ArrayList<>(group.size());
        List<RecipeTreeNodeViewModel> parents = new ArrayList<>(group.size());
        int total = 0;
        for (UnresolvedInputSlot slot : group) {
            members.add(slot.input());
            parents.add(slot.parentNode());
            total = safeAdd(total, Math.max(1, slot.input().amount()));
        }
        return new MergedLeaf(resolveGroupIngredient(members), displayNameOf(representative), total, representative.amountText(),
                anchorParent, List.copyOf(members), List.copyOf(parents));
    }

    private boolean wouldCauseRecursiveLeafExpansion(MergedLeaf leaf, RecipeTreeRecipeViewModel chosen) {
        for (int i = 0; i < leaf.members().size(); i++) {
            if (leaf.parentForMember(i).containsRecipe(chosen)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecipeSnapshotStrictEncodable(RecipeTreeRecipeViewModel recipe) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        return backend == null ? recipe.primaryOutputIngredient() != null : backend.isStrictEncodable(recipe);
    }

    private Optional<RecipeTreeRecipeViewModel> resolveUniqueEncodableRecipe(String focusSignature, ITypedIngredient<?> focus) {
        if (focusSignature == null || focusSignature.isBlank()) {
            return Optional.empty();
        }
        Optional<RecipeTreeRecipeViewModel> cached = autoExpandUniqueCandidateCache.get(focusSignature);
        if (cached != null) {
            return cached;
        }
        List<RecipeTreeRecipeViewModel> recipes = RecipeTreeJeiLookup.findRecipesByOutput(focus);
        RecipeTreeRecipeViewModel chosen = null;
        for (RecipeTreeRecipeViewModel recipe : recipes) {
            if (!isRecipeSnapshotStrictEncodable(recipe)) {
                continue;
            }
            if (chosen != null) {
                autoExpandUniqueCandidateCache.put(focusSignature, Optional.empty());
                return Optional.empty();
            }
            chosen = recipe;
        }
        Optional<RecipeTreeRecipeViewModel> resolved = Optional.ofNullable(chosen);
        autoExpandUniqueCandidateCache.put(focusSignature, resolved);
        return resolved;
    }

    private record NodeCrafts(RecipeTreeNodeViewModel node, int crafts) {
    }

    private record CachedInputSignature(int alternativeIndex, String signature) {
    }

    private record LayerTraversalEntry(RecipeTreeNodeViewModel node, int crafts, int depth) {
    }

    private record LayerMaterialKey(int kind, String ingredientSignature, @Nullable String expansionSignature) {
    }

    private record CachedLayerMaterialKey(int alternativeIndex, @Nullable RecipeTreeNodeViewModel child,
            LayerMaterialKey key) {
    }

    private record UnresolvedInputSlot(RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parentNode) {
    }

    private PositionedNode layoutNode(GraphNode node, int depth, int left) {
        int y = 42 + TOP_MATERIALS_OFFSET + depth * LEVEL_GAP;
        int subtreeWidth = computeSubtreeWidth(node);
        int x = left + Math.max(0, (subtreeWidth - node.width()) / 2);
        PositionedNode positioned = new PositionedNode(node, x, y);
        positionedNodes.add(positioned);
        positionedNodeRows.computeIfAbsent(y, ignored -> new ArrayList<>()).add(positioned);

        if (node.children().isEmpty()) {
            return positioned;
        }

        int childrenWidth = totalChildrenWidth(node.children());
        int childLeft = left + Math.max(0, (subtreeWidth - childrenWidth) / 2);
        for (GraphNode child : node.children()) {
            PositionedNode childPositioned = layoutNode(child, depth + 1, childLeft);
            Edge edge = new Edge(positioned, childPositioned);
            edges.add(edge);
            edgeRows.computeIfAbsent(positioned.y(), ignored -> new ArrayList<>()).add(edge);
            childLeft += computeSubtreeWidth(child) + LEAF_GAP;
        }
        return positioned;
    }

    private void finalizeSpatialIndex() {
        for (List<PositionedNode> row : positionedNodeRows.values()) {
            row.sort((left, right) -> Integer.compare(left.x(), right.x()));
        }
        for (List<Edge> row : edgeRows.values()) {
            row.sort((left, right) -> Integer.compare(edgeMinX(left), edgeMinX(right)));
        }
        for (LayerRow row : mergedLayerRows) {
            int size = row.materials().size();
            int[] leftOffsets = new int[size];
            int[] rightOffsets = new int[size];
            int[] centerOffsets = new int[size];
            int currentX = 0;
            for (int i = 0; i < size; i++) {
                LayerMaterial material = row.materials().get(i);
                leftOffsets[i] = currentX;
                rightOffsets[i] = currentX + material.width();
                centerOffsets[i] = currentX + material.width() / 2;
                currentX += material.width() + 4;
            }
            int rowWidth = size == 0 ? 24 : Math.max(24, currentX - 4);
            int anchorCenterOffset = size == 0 ? rowWidth / 2
                    : (centerOffsets[0] + centerOffsets[size - 1]) / 2;
            mergedRowRenderCaches.put(row,
                    new LayerRowRenderCache(leftOffsets, rightOffsets, centerOffsets, rowWidth, anchorCenterOffset));
        }
    }

    private static int edgeMinX(Edge edge) {
        return Math.min(edge.parent().x(), edge.child().x());
    }

    private static int edgeMaxX(Edge edge) {
        return Math.max(edge.parent().x() + edge.parent().graph().width(),
                edge.child().x() + edge.child().graph().width());
    }

    private int computeSubtreeWidth(GraphNode node) {
        Integer cached = graphSubtreeWidthCache.get(node);
        if (cached != null) {
            return cached;
        }
        int width = node.children().isEmpty()
                ? node.width()
                : Math.max(node.width(), totalChildrenWidth(node.children()));
        graphSubtreeWidthCache.put(node, width);
        return width;
    }

    private int totalChildrenWidth(List<GraphNode> children) {
        int total = 0;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                total += LEAF_GAP;
            }
            total += computeSubtreeWidth(children.get(i));
        }
        return total;
    }

    private GraphNode buildGraph(RecipeTreeNodeViewModel node, int crafts) {
        List<GraphNode> children = new ArrayList<>();
        List<RecipeTreeInputViewModel> orderedInputs = new ArrayList<>(node.recipe().inputs());
        List<InputCluster> clusters = autoMergeSameMaterials
                ? mergeSiblingGroupsByLayer(node, orderedInputs)
                : clusterInputsBySignature(orderedInputs);
        clusters = consolidateClustersSharingExpandedChild(clusters);

        for (InputCluster cluster : clusters) {
            List<RecipeTreeInputViewModel> group = cluster.inputs();
            if (group.isEmpty()) {
                continue;
            }

            RecipeTreeInputViewModel representative = group.getFirst();
            int totalRequiredAmount = 0;
            for (RecipeTreeInputViewModel input : group) {
                int amount = safeMultiply(crafts, input.amount());
                totalRequiredAmount = safeAdd(totalRequiredAmount, Math.max(1, amount));
            }

            RecipeTreeNodeViewModel child = representative.child();

            if (child != null) {
                int childCrafts = ceilDiv(totalRequiredAmount, child.recipe().primaryOutputCount());
                children.add(buildGraph(child, childCrafts));
            } else {
                addMergedLeafChild(node, children, group, totalRequiredAmount);
            }
        }

        String amountLabel = "";
        String exactAmountLabel = "";
        if (computeRecipeQuantities) {
            amountLabel = formatCompactCount(crafts);
            exactAmountLabel = Component.translatable("gui.jeict.recipe_tree.amount_exact", crafts).getString();
        }
        return new GraphNode(node.recipe().primaryOutputIngredient(), node.recipe().title().getString(), amountLabel,
                exactAmountLabel,
                node, node.recipe().subtitleIcon(), node.recipe().subtitle(), null, false, children,
                computeNodeWidth(node.recipe().title().getString(), amountLabel,
                        node.recipe().subtitleIcon() != null, false, node.parent() != null && !children.isEmpty()));
    }

    private List<LayerRow> buildMergedLayerRows(RecipeTreeNodeViewModel root, int crafts) {
        MergedBuildStats stats = new MergedBuildStats();
        List<LayerAccumulator> layers = new ArrayList<>();
        ArrayDeque<LayerTraversalEntry> pending = new ArrayDeque<>();
        pending.addLast(new LayerTraversalEntry(root, crafts, 0));
        while (!pending.isEmpty()) {
            LayerTraversalEntry entry = pending.removeFirst();
            RecipeTreeNodeViewModel node = entry.node();
            int depth = entry.depth();
            stats.collectCalls++;
            layerAt(layers, depth).addNode(node, entry.crafts());
            stats.recipeNodeAdds++;

            RecipeTreeNodeViewModel firstChild = null;
            int firstChildAmount = 0;
            Map<RecipeTreeNodeViewModel, Integer> multipleChildren = null;
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                RecipeTreeNodeViewModel child = input.child();
                int amount = Math.max(1, safeMultiply(entry.crafts(), input.amount()));
                if (child == null) {
                    layerAt(layers, depth + 1).addLeaf(input, amount, node);
                    stats.leafAdds++;
                    continue;
                }
                stats.aggregatedChildLinks++;
                if (firstChild == null) {
                    firstChild = child;
                    firstChildAmount = amount;
                } else if (multipleChildren == null && child == firstChild) {
                    firstChildAmount = safeAdd(firstChildAmount, amount);
                } else {
                    if (multipleChildren == null) {
                        multipleChildren = new IdentityHashMap<>();
                        multipleChildren.put(firstChild, firstChildAmount);
                    }
                    multipleChildren.merge(child, amount, RecipeTreeOverviewScreen::safeAdditiveMergeTotals);
                }
            }
            if (multipleChildren != null) {
                for (Map.Entry<RecipeTreeNodeViewModel, Integer> childEntry : multipleChildren.entrySet()) {
                    RecipeTreeNodeViewModel child = childEntry.getKey();
                    pending.addLast(new LayerTraversalEntry(child,
                            ceilDiv(childEntry.getValue(), child.recipe().primaryOutputCount()), depth + 1));
                }
            } else if (firstChild != null) {
                pending.addLast(new LayerTraversalEntry(firstChild,
                        ceilDiv(firstChildAmount, firstChild.recipe().primaryOutputCount()), depth + 1));
            }
        }

        List<LayerRow> rows = new ArrayList<>(layers.size());
        int materialCount = 0;
        for (LayerAccumulator layer : layers) {
            LayerRow row = layer.toRow();
            rows.add(row);
            materialCount += row.materials().size();
        }
        List<LayerRow> built = List.copyOf(rows);
        stats.layerRows = built.size();
        stats.layerMaterials = materialCount;
        lastMergedBuildStats = stats;
        return built;
    }

    private LayerAccumulator layerAt(List<LayerAccumulator> layers, int depth) {
        while (layers.size() <= depth) {
            layers.add(null);
        }
        LayerAccumulator layer = layers.get(depth);
        if (layer == null) {
            layer = new LayerAccumulator(depth);
            layers.set(depth, layer);
        }
        return layer;
    }

    private static int safeAdditiveMergeTotals(int left, int right) {
        return safeAdd(left, right);
    }

    private LayerMaterialKey leafAggregateStorageKey(RecipeTreeInputViewModel input) {
        int alternativeIndex = input.selectedAlternativeIndex();
        RecipeTreeNodeViewModel child = input.child();
        CachedLayerMaterialKey cached = leafLayerKeyCache.get(input);
        if (cached != null && cached.alternativeIndex() == alternativeIndex && cached.child() == child) {
            return cached.key();
        }
        String expansionSignature = null;
        if (child != null) {
            ResourceLocation id = child.recipe().recipeId();
            expansionSignature = id != null
                    ? id.toString()
                    : child.recipe().title().getString() + "|" + signatureOf(child.recipe().primaryOutputIngredient());
        }
        LayerMaterialKey key = new LayerMaterialKey(1, leafSignatureOf(input), expansionSignature);
        leafLayerKeyCache.put(input, new CachedLayerMaterialKey(alternativeIndex, child, key));
        return key;
    }

    private LayerMaterialKey recipeOutputStorageKey(RecipeTreeNodeViewModel node) {
        LayerMaterialKey cached = recipeLayerKeyCache.get(node);
        if (cached != null) {
            return cached;
        }
        ITypedIngredient<?> ingredient = node.recipe().primaryOutputIngredient();
        LayerMaterialKey key = new LayerMaterialKey(0,
                ingredient != null ? signatureOf(ingredient) : signatureOfNode(node), null);
        recipeLayerKeyCache.put(node, key);
        return key;
    }

    private List<InputCluster> clusterInputsBySignature(List<RecipeTreeInputViewModel> orderedInputs) {
        Map<String, List<RecipeTreeInputViewModel>> groupedInputs = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : orderedInputs) {
            groupedInputs.computeIfAbsent(leafSignatureOf(input), k -> new ArrayList<>()).add(input);
        }
        List<InputCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<RecipeTreeInputViewModel>> entry : groupedInputs.entrySet()) {
            clusters.add(new InputCluster(entry.getKey(), 0, new ArrayList<>(entry.getValue())));
        }
        return clusters;
    }

    private List<InputCluster> mergeSiblingGroupsByLayer(RecipeTreeNodeViewModel node,
            List<RecipeTreeInputViewModel> orderedInputs) {
        Map<LayerKey, List<RecipeTreeInputViewModel>> merged = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : orderedInputs) {
            String signature = leafSignatureOf(input);
            int layer = layerOf(input.child(), node);
            merged.computeIfAbsent(new LayerKey(signature, layer), ignored -> new ArrayList<>()).add(input);
        }
        List<InputCluster> clusters = new ArrayList<>();
        for (Map.Entry<LayerKey, List<RecipeTreeInputViewModel>> entry : merged.entrySet()) {
            clusters.add(new InputCluster(entry.getKey().signature(), entry.getKey().layer(), new ArrayList<>(entry.getValue())));
        }
        return clusters;
    }

    private int computeMergedLayerContentWidth() {
        return Math.max(1, cachedMergedContentWidth);
    }

    private List<GenericTopMaterialRenderData> collectGenericTopMaterialRenderData() {
        unresolvedInputsBySignature.clear();
        Map<String, ItemTopMaterialAccumulator> itemMaterials = new LinkedHashMap<>();
        Map<String, GenericTopMaterialAccumulator> genericMaterials = new LinkedHashMap<>();
        collectGenericTopMaterials(context.root(), batchCount, itemMaterials, genericMaterials);
        List<RequestedIngredient> rebuiltTopMaterials = new ArrayList<>(itemMaterials.size());
        for (Map.Entry<String, ItemTopMaterialAccumulator> entry : itemMaterials.entrySet()) {
            ItemTopMaterialAccumulator accumulator = entry.getValue();
            rebuiltTopMaterials.add(accumulator.toRequestedIngredient());
            unresolvedInputsBySignature.put(entry.getKey(), accumulator.slots());
        }
        topMaterials = List.copyOf(rebuiltTopMaterials);
        List<GenericTopMaterialRenderData> result = new ArrayList<>(genericMaterials.size());
        for (GenericTopMaterialAccumulator accumulator : genericMaterials.values()) {
            MergedLeaf leaf = accumulator.toLeaf();
            unresolvedInputsBySignature.put(leafSignatureOf(leaf.representative()), accumulator.slots());
            String label = computeRecipeQuantities
                    ? formatLayerMaterialAmountLabel(leaf.ingredient(), leaf.members(), leaf.totalAmount())
                    : "";
            int width = 24 + (label.isEmpty() ? 0 : this.font.width(label) + 6);
            if (leaf.representative().hasAlternativeChoices()) {
                width += 14;
            }
            result.add(new GenericTopMaterialRenderData(leaf, label, width));
        }
        return List.copyOf(result);
    }

    private void collectGenericTopMaterials(RecipeTreeNodeViewModel root, int rootCrafts,
            Map<String, ItemTopMaterialAccumulator> itemMaterials,
            Map<String, GenericTopMaterialAccumulator> genericMaterials) {
        ArrayDeque<NodeCrafts> pending = new ArrayDeque<>();
        pending.addLast(new NodeCrafts(root, rootCrafts));
        while (!pending.isEmpty()) {
            NodeCrafts current = pending.removeLast();
            RecipeTreeNodeViewModel node = current.node();
            int crafts = current.crafts();
            RecipeTreeNodeViewModel firstChild = null;
            int firstChildAmount = 0;
            Map<RecipeTreeNodeViewModel, Integer> multipleChildren = null;
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                RecipeTreeNodeViewModel child = input.child();
                int amount = safeMultiply(crafts, input.amount());
                if (child != null) {
                    if (firstChild == null) {
                        firstChild = child;
                        firstChildAmount = amount;
                    } else if (multipleChildren == null && child == firstChild) {
                        firstChildAmount = safeAdd(firstChildAmount, amount);
                    } else {
                        if (multipleChildren == null) {
                            multipleChildren = new IdentityHashMap<>();
                            multipleChildren.put(firstChild, firstChildAmount);
                        }
                        multipleChildren.merge(child, amount, RecipeTreeOverviewScreen::safeAdd);
                    }
                    continue;
                }
                RequestedIngredient requested = input.requestedIngredientView();
                String signature = leafSignatureOf(input);
                if (requested != null && !requested.alternatives().isEmpty()) {
                    itemMaterials.computeIfAbsent(signature, ignored -> new ItemTopMaterialAccumulator(input))
                            .add(input, node, amount);
                    continue;
                }
                genericMaterials.computeIfAbsent(signature, ignored -> new GenericTopMaterialAccumulator(input, node))
                        .add(input, node, amount);
            }
            if (multipleChildren != null) {
                for (Map.Entry<RecipeTreeNodeViewModel, Integer> entry : multipleChildren.entrySet()) {
                    RecipeTreeNodeViewModel child = entry.getKey();
                    pending.addLast(new NodeCrafts(child,
                            ceilDiv(entry.getValue(), child.recipe().primaryOutputCount())));
                }
            } else if (firstChild != null) {
                pending.addLast(new NodeCrafts(firstChild,
                        ceilDiv(firstChildAmount, firstChild.recipe().primaryOutputCount())));
            }
        }
    }

    private void refreshTopMaterialRenderCacheIfNeeded() {
        Inventory inventory = currentTopMaterialInventory();
        int inventoryVersion = computeRecipeQuantities && inventory != null ? inventory.getTimesChanged() : -1;
        if (!topMaterialRenderCacheDirty
                && inventory == topMaterialInventory
                && inventoryVersion == topMaterialInventoryVersion
                && topMaterialRenderData.size() == topMaterials.size()
                && surplusMaterialRenderData.size() == surplusMaterials.size()) {
            return;
        }
        boolean rebuildPatternHints = topMaterialPatternHintsDirty
                || topMaterialRenderData.size() != topMaterials.size();
        List<TopMaterialRenderData> previous = topMaterialRenderData;
        List<TopMaterialRenderData> rebuilt = new ArrayList<>(topMaterials.size());
        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient material = topMaterials.get(i);
            int displayCount = computeRecipeQuantities ? Math.max(1, material.count()) : 1;
            String label = computeRecipeQuantities ? "x" + formatCompactCount(displayCount) : "";
            int width = 24 + (label.isEmpty() ? 0 : this.font.width(label) + 6);
            if (material.alternatives().size() > 1) {
                width += 14;
            }
            ItemStack stack = ItemStack.EMPTY;
            if (!material.alternatives().isEmpty()) {
                stack = material.alternatives().getFirst().copyWithCount(displayCount);
            }
            boolean showsPatternHint = !rebuildPatternHints
                    && previous.get(i).material() == material
                    ? previous.get(i).showsPatternHint()
                    : shouldShowTopMaterialPatternHint(material);
            rebuilt.add(new TopMaterialRenderData(material, displayCount,
                    inventoryAdjustedLabelColor(material, displayCount), label, width, stack, showsPatternHint));
        }
        topMaterialRenderData = List.copyOf(rebuilt);
        List<TopMaterialRenderData> rebuiltSurplus = new ArrayList<>(surplusMaterials.size());
        for (RequestedIngredient material : surplusMaterials) {
            int displayCount = Math.max(1, material.count());
            String label = "x" + formatCompactCount(displayCount);
            int width = 24 + this.font.width(label) + 6;
            ItemStack stack = material.alternatives().isEmpty()
                    ? ItemStack.EMPTY
                    : material.alternatives().getFirst().copyWithCount(displayCount);
            rebuiltSurplus.add(new TopMaterialRenderData(material, displayCount, RecipeTreeTheme.current().enough(),
                    label, width, stack, false));
        }
        surplusMaterialRenderData = List.copyOf(rebuiltSurplus);
        cachedMissingMaterialCount = 0;
        if (computeRecipeQuantities) {
            int enoughColor = RecipeTreeTheme.current().enough();
            for (TopMaterialRenderData data : rebuilt) {
                if (data.labelColor() != enoughColor) {
                    cachedMissingMaterialCount++;
                }
            }
        }
        topMaterialInventory = inventory;
        topMaterialInventoryVersion = inventoryVersion;
        topMaterialRenderCacheDirty = false;
        topMaterialPatternHintsDirty = false;
    }

    private static @Nullable Inventory currentTopMaterialInventory() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getInventory();
    }

    private void refreshMergedContentWidth() {
        int maxLeftExtent = 0;
        int maxRightExtent = 1;
        for (LayerRow row : mergedLayerRows) {
            int currentX = 0;
            int firstCenter = NODE_MAIN_SIZE / 2;
            int lastCenter = firstCenter;
            for (int i = 0; i < row.materials().size(); i++) {
                LayerMaterial material = row.materials().get(i);
                int center = currentX + material.width() / 2;
                if (i == 0) {
                    firstCenter = center;
                }
                lastCenter = center;
                currentX += material.width() + 4;
            }
            int rowWidth = row.materials().isEmpty() ? 24 : Math.max(24, currentX - 4);
            int anchorCenter = (firstCenter + lastCenter) / 2;
            maxLeftExtent = Math.max(maxLeftExtent, anchorCenter);
            maxRightExtent = Math.max(maxRightExtent, rowWidth - anchorCenter);
        }
        cachedMergedAnchorOffset = maxLeftExtent;
        cachedMergedContentWidth = Math.max(1, maxLeftExtent + maxRightExtent);
    }

    private int computeMergedLayerContentHeight() {
        if (mergedLayerRows.isEmpty()) {
            return NODE_HEIGHT;
        }
        return mergedLayerRows.size() * LEVEL_GAP;
    }

    private void fitInitialTreeView(PositionedNode root) {
        double minX = root.x();
        double minY = root.y() - NODE_HEIGHT - 28.0D;
        double maxX = root.x() + root.graph().width();
        double maxY = root.y() + NODE_HEIGHT;
        for (PositionedNode node : positionedNodes) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + node.graph().width());
            maxY = Math.max(maxY, node.y() + NODE_HEIGHT);
        }
        fitInitialView(minX, minY, maxX, maxY);
    }

    private void fitInitialView(double minX, double minY, double maxX, double maxY) {
        double contentWidth = Math.max(1.0D, maxX - minX);
        double contentHeight = Math.max(1.0D, maxY - minY);
        double availableWidth = Math.max(1.0D, canvasRight() - canvasLeft() - 32.0D);
        double availableHeight = Math.max(1.0D, this.height - HEADER_HEIGHT - currentFooterHeight() - 36.0D);
        double fitted = Math.min(availableWidth / (contentWidth + 48.0D),
                availableHeight / (contentHeight + 36.0D));
        zoom = Math.max(INITIAL_MIN_ZOOM, Math.min(INITIAL_MAX_ZOOM, fitted));
        double screenCenterX = (canvasLeft() + canvasRight()) * 0.5D;
        double screenCenterY = (HEADER_HEIGHT + this.height - currentFooterHeight()) * 0.5D;
        panX = screenCenterX - (minX + maxX) * 0.5D * zoom;
        panY = screenCenterY - (minY + maxY) * 0.5D * zoom;
    }

    private void fitCurrentView() {
        if (autoMergeSameMaterials) {
            if (mergedLayerRows.isEmpty()) {
                return;
            }
            int contentWidth = computeMergedLayerContentWidth();
            int contentHeight = computeMergedLayerContentHeight();
            double left = 36.0D;
            double top = 42.0D + TOP_MATERIALS_OFFSET - NODE_HEIGHT - 28.0D;
            fitInitialView(left, top, left + contentWidth, 42.0D + TOP_MATERIALS_OFFSET + contentHeight);
        } else if (!positionedNodes.isEmpty()) {
            fitInitialTreeView(positionedNodes.getFirst());
        }
        updateSelectionButtons();
    }

    private void zoomAtCenter(double delta) {
        double centerX = (canvasLeft() + canvasRight()) * 0.5D;
        double centerY = (HEADER_HEIGHT + this.height - currentFooterHeight()) * 0.5D;
        zoomAt(centerX, centerY, delta);
    }

    private void zoomAt(double screenX, double screenY, double delta) {
        double oldZoom = zoom;
        zoom = Math.max(0.2D, Math.min(4.0D, zoom + delta));
        double logicalX = (screenX - panX) / oldZoom;
        double logicalY = (screenY - panY) / oldZoom;
        panX = screenX - logicalX * zoom;
        panY = screenY - logicalY * zoom;
        updateSelectionButtons();
    }



    private @Nullable RootBatchAnchor updateBatchBadgeBounds() {
        RootBatchAnchor anchor = findRootBatchAnchor();
        if (anchor == null) {
            batchBadgeBounds = null;
            return null;
        }
        String label = batchCountLabel();
        int logicalX = anchor.right() + BATCH_TEXT_GAP;
        int logicalY = anchor.y() + Math.max(0, (NODE_HEIGHT - this.font.lineHeight) / 2);
        int logicalWidth = this.font.width(label);
        int logicalHeight = this.font.lineHeight;
        if (!isLogicalRectVisible(logicalX, logicalY,
                logicalX + logicalWidth, logicalY + logicalHeight)) {
            batchBadgeBounds = null;
            return null;
        }
        int x = (int) Math.floor(logicalX * zoom + panX);
        int y = (int) Math.floor(logicalY * zoom + panY);
        int right = (int) Math.ceil((logicalX + logicalWidth) * zoom + panX);
        int bottom = (int) Math.ceil((logicalY + logicalHeight) * zoom + panY);
        int footerTop = this.height - currentFooterHeight();
        boolean visible = x < canvasRight() - 2
                && right > canvasLeft() + 2
                && y < footerTop - 2
                && bottom > HEADER_HEIGHT + 2;
        batchBadgeBounds = visible
                ? new BatchBadgeBounds(x, y, Math.max(1, right - x), Math.max(1, bottom - y))
                : null;
        return visible ? anchor : null;
    }

    private @Nullable RootBatchAnchor findRootBatchAnchor() {
        if (autoMergeSameMaterials) {
            if (mergedLayerRows.isEmpty()) {
                return null;
            }
            LayerRow rootRow = mergedLayerRows.getFirst();
            LayerRowRenderCache cache = mergedRowRenderCaches.get(rootRow);
            if (cache == null || cache.rightOffsets().length == 0) {
                return null;
            }
            int rowX = mergedRowX(36, cache);
            int left = rowX + cache.leftOffsets()[0];
            int right = rowX + cache.rightOffsets()[cache.rightOffsets().length - 1];
            return new RootBatchAnchor(left, right, 42 + TOP_MATERIALS_OFFSET);
        }
        if (rootNode == null) {
            return null;
        }
        for (PositionedNode node : positionedNodes) {
            if (node.graph() == rootNode || node.graph().recipeNode() == context.root()) {
                return new RootBatchAnchor(node.x(), node.x() + node.graph().width(), node.y());
            }
        }
        if (!positionedNodes.isEmpty()) {
            PositionedNode root = positionedNodes.getFirst();
            return new RootBatchAnchor(root.x(), root.x() + root.graph().width(), root.y());
        }
        return null;
    }

    private void adjustBatchCount(int delta) {
        if (delta != 0) {
            setBatchCount(safeAdd(batchCount, delta));
        }
    }

    private void setBatchCount(int count) {
        int normalized = Math.max(1, count);
        if (normalized == batchCount) {
            return;
        }
        batchCount = normalized;
        refreshRenderedProjection();
    }

    private String batchCountLabel() {
        return "x" + formatCompactCount(batchCount);
    }


    private void ensureLogicalRectVisible(int x, int y, int width, int height) {
        double screenLeft = x * zoom + panX;
        double screenTop = y * zoom + panY;
        double screenRight = (x + width) * zoom + panX;
        double screenBottom = (y + height) * zoom + panY;
        int margin = 18;
        if (screenRight > canvasRight() - margin) {
            panX -= screenRight - (canvasRight() - margin);
        }
        if (screenLeft < canvasLeft() + margin) {
            panX += canvasLeft() + margin - screenLeft;
        }
        if (screenBottom > this.height - currentFooterHeight() - margin) {
            panY -= screenBottom - (this.height - currentFooterHeight() - margin);
        }
        if (screenTop < HEADER_HEIGHT + margin) {
            panY += HEADER_HEIGHT + margin - screenTop;
        }
    }

    private @Nullable LayerMaterialBounds findLayerAt(double logicalMouseX, double logicalMouseY) {
        for (LayerMaterialBounds bounds : layerMaterialBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                return bounds;
            }
        }
        return null;
    }

    private int layerOf(@Nullable RecipeTreeNodeViewModel child, RecipeTreeNodeViewModel parent) {
        if (child == null) {
            return Integer.MAX_VALUE;
        }
        int depth = 0;
        for (RecipeTreeNodeViewModel cursor = child; cursor != null && cursor != parent; cursor = cursor.parent()) {
            depth++;
        }
        return depth;
    }

    private record LayerKey(String signature, int layer) {
    }

    private record InputCluster(String signature, int layer, List<RecipeTreeInputViewModel> inputs) {
    }

    /** 多只输入格子指向同一已展开 {@link RecipeTreeNodeViewModel} 时合成单簇，与合并层上对子树去重递归一致，减少 GraphNode 分支。 */
    private List<InputCluster> consolidateClustersSharingExpandedChild(List<InputCluster> clusters) {
        LinkedHashMap<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> expandedByChild = new LinkedHashMap<>();
        for (InputCluster cluster : clusters) {
            RecipeTreeInputViewModel rep = cluster.inputs().getFirst();
            RecipeTreeNodeViewModel ch = rep.child();
            if (ch != null) {
                expandedByChild.computeIfAbsent(ch, ignored -> new ArrayList<>()).addAll(cluster.inputs());
            }
        }
        if (expandedByChild.isEmpty()) {
            return clusters;
        }
        Set<RecipeTreeNodeViewModel> emittedChild = new HashSet<>();
        List<InputCluster> merged = new ArrayList<>(clusters.size());
        for (InputCluster cluster : clusters) {
            RecipeTreeInputViewModel rep = cluster.inputs().getFirst();
            RecipeTreeNodeViewModel ch = rep.child();
            if (ch != null) {
                if (!emittedChild.add(ch)) {
                    continue;
                }
                List<RecipeTreeInputViewModel> grouped = expandedByChild.get(ch);
                RecipeTreeInputViewModel head = grouped.getFirst();
                merged.add(new InputCluster(leafSignatureOf(head), cluster.layer(), new ArrayList<>(grouped)));
            } else {
                merged.add(cluster);
            }
        }
        return merged;
    }

    private record LayerRow(int depth, List<LayerMaterial> materials) {
        private int width() {
            int total = 0;
            for (int i = 0; i < materials.size(); i++) {
                if (i > 0) {
                    total += 4;
                }
                total += materials.get(i).width();
            }
            return Math.max(24, total);
        }
    }

    private record LayerMaterial(@Nullable ITypedIngredient<?> ingredient, String label, String amountLabel, int width,
            boolean hasAlternatives, int totalAmount, List<RecipeTreeNodeViewModel> recipeTargets,
            List<RecipeTreeInputViewModel> leafInputs, List<RecipeTreeNodeViewModel> leafParents,
            @Nullable IDrawable machineIcon, @Nullable Component machineName, boolean showsPatternHint,
            @Nullable MergedLeaf leafProjection) {
        private LayerMaterial {
            recipeTargets = recipeTargets == null ? List.of() : List.copyOf(recipeTargets);
            leafInputs = leafInputs == null ? List.of() : List.copyOf(leafInputs);
            leafParents = leafParents == null ? List.of() : List.copyOf(leafParents);
        }

        LayerMaterial withShowsPatternHint(boolean hint) {
            return hint == showsPatternHint
                    ? this
                    : new LayerMaterial(ingredient, label, amountLabel, width, hasAlternatives, totalAmount, recipeTargets,
                            leafInputs, leafParents, machineIcon, machineName, hint, leafProjection);
        }

        private boolean hasUnresolvedLeaves() {
            return leafInputs.stream().anyMatch(input -> input.child() == null);
        }
    }

    private record LayerMaterialBounds(LayerRow row, LayerMaterial material, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final class LayerAccumulator {
        private final int depth;
        private final Map<LayerMaterialKey, LayerMaterialAggregate> materials = new LinkedHashMap<>();

        private LayerAccumulator(int depth) {
            this.depth = depth;
        }

        private void addNode(RecipeTreeNodeViewModel node, int crafts) {
            LayerMaterialKey storageKey = recipeOutputStorageKey(node);
            LayerMaterialAggregate aggregate = materials.get(storageKey);
            ITypedIngredient<?> ingredient = node.recipe().primaryOutputIngredient();
            String label = null;
            if (aggregate == null) {
                label = displayNameOfOutput(node);
                aggregate = new LayerMaterialAggregate(ingredient, label, false);
                materials.put(storageKey, aggregate);
            }
            int amount = safeMultiply(crafts, node.recipe().primaryOutputCount());
            aggregate.addRecipeNode(node, amount, ingredient, label);
        }

        private void addLeaf(RecipeTreeInputViewModel input, int amount, RecipeTreeNodeViewModel parentNode) {
            LayerMaterialKey storageKey = leafAggregateStorageKey(input);
            LayerMaterialAggregate aggregate = materials.get(storageKey);
            ITypedIngredient<?> resolvedIngredient = null;
            String label = null;
            if (aggregate == null) {
                resolvedIngredient = resolveDisplayIngredient(input);
                label = displayNameOf(input);
                aggregate = new LayerMaterialAggregate(resolvedIngredient, label, input.hasAlternativeChoices());
                materials.put(storageKey, aggregate);
            }
            aggregate.addLeaf(input, amount, parentNode, resolvedIngredient, label, input.hasAlternativeChoices());
        }

        private LayerRow toRow() {
            List<LayerMaterial> rowMaterials = new ArrayList<>();
            for (LayerMaterialAggregate aggregate : materials.values()) {
                rowMaterials.add(aggregate.toLayerMaterial());
            }
            return new LayerRow(depth, List.copyOf(rowMaterials));
        }
    }

    private final class LayerMaterialAggregate {
        private @Nullable ITypedIngredient<?> ingredient;
        private String label;
        private boolean hasAlternatives;
        private final List<RecipeTreeNodeViewModel> recipeTargets = new ArrayList<>();
        private final List<RecipeTreeInputViewModel> leafInputs = new ArrayList<>();
        private final List<RecipeTreeNodeViewModel> leafParents = new ArrayList<>();
        private int amount;
        private @Nullable IDrawable machineIcon;
        private @Nullable Component machineName;

        private LayerMaterialAggregate(@Nullable ITypedIngredient<?> ingredient, String label, boolean initialAlternatives) {
            this.ingredient = ingredient;
            this.label = label == null ? "" : label;
            this.hasAlternatives = initialAlternatives;
            this.amount = 0;
            this.machineIcon = null;
            this.machineName = null;
        }

        void addRecipeNode(RecipeTreeNodeViewModel node, int contribution, @Nullable ITypedIngredient<?> ing, String nodeLabel) {
            recipeTargets.add(node);
            amount = safeAdd(amount, Math.max(1, contribution));
            if (ingredient == null && ing != null) {
                ingredient = ing;
            }
            if (nodeLabel != null && !nodeLabel.isBlank()) {
                label = nodeLabel;
            }
            IDrawable icon = node.recipe().subtitleIcon();
            if (machineIcon == null && icon != null) {
                machineIcon = icon;
            }
            if (machineName == null && node.recipe().subtitle() != null) {
                machineName = node.recipe().subtitle();
            }
        }

        void addLeaf(RecipeTreeInputViewModel input, int contribution, RecipeTreeNodeViewModel parentNode,
                @Nullable ITypedIngredient<?> ing, String leafLabel, boolean alternatives) {
            leafInputs.add(input);
            leafParents.add(parentNode);
            amount = safeAdd(amount, Math.max(1, contribution));
            hasAlternatives |= alternatives;
            if (ingredient == null && ing != null) {
                ingredient = ing;
            }
            if (leafLabel != null && !leafLabel.isBlank()) {
                label = leafLabel;
            }
        }

        LayerMaterial toLayerMaterial() {
            int displayAmount = Math.max(1, amount);
            String amountLabel = computeRecipeQuantities
                    ? formatLayerMaterialAmountLabel(ingredient, List.copyOf(leafInputs), displayAmount)
                    : "";
            boolean showAlternativesButton = hasAlternatives && leafInputs.stream().anyMatch(input -> input.child() == null);
            int slotWidth = computeNodeWidth(label, amountLabel, machineIcon != null, showAlternativesButton, false);
            boolean hint = RecipeTreeOverviewScreen.this.computeLayerMaterialShowsPatternHint(ingredient, label, amount,
                    List.copyOf(recipeTargets), List.copyOf(leafInputs), List.copyOf(leafParents));
            MergedLeaf projection = null;
            if (!leafInputs.isEmpty()) {
                RecipeTreeInputViewModel rep = leafInputs.getFirst();
                RecipeTreeNodeViewModel primaryParent = leafParents.getFirst();
                projection = new MergedLeaf(ingredient, label, Math.max(1, amount), rep.amountText(), primaryParent,
                        List.copyOf(leafInputs), List.copyOf(leafParents));
            }
            return new LayerMaterial(ingredient, label, amountLabel, slotWidth, showAlternativesButton, amount,
                    List.copyOf(recipeTargets), List.copyOf(leafInputs), List.copyOf(leafParents), machineIcon, machineName, hint,
                    projection);
        }
    }




    private @Nullable MergedLeaf mergedLeafFromLayerMaterial(LayerMaterial material) {
        return material.leafProjection();
    }

    private boolean computeLayerMaterialShowsPatternHint(LayerMaterial material) {
        return computeLayerMaterialShowsPatternHint(material.ingredient(), material.label(), material.totalAmount(),
                material.recipeTargets(), material.leafInputs(), material.leafParents());
    }

    private boolean computeLayerMaterialShowsPatternHint(@Nullable ITypedIngredient<?> ingredient, String label, int totalAmount,
            List<RecipeTreeNodeViewModel> recipeTargets, List<RecipeTreeInputViewModel> leafInputs,
            List<RecipeTreeNodeViewModel> leafParents) {
        if (!shouldShowMeExistingPatternHints()) {
            return false;
        }
        if (!leafInputs.isEmpty() && !leafParents.isEmpty()) {
            RecipeTreeInputViewModel representative = leafInputs.getFirst();
            RecipeTreeNodeViewModel primaryParent = leafParents.getFirst();
            MergedLeaf leaf = new MergedLeaf(ingredient, label, totalAmount, representative.amountText(), primaryParent,
                    List.copyOf(leafInputs), List.copyOf(leafParents));
            if (hasExistingPatternForLeaf(leaf)) {
                return true;
            }
        }
        for (RecipeTreeNodeViewModel node : recipeTargets) {
            if (hasExistingPatternForOutput(node.recipe())) {
                return true;
            }
        }
        return false;
    }

    /** 仅在「已有样板:禁」且开启数量演算时，才做 ME 已有样板检测与红框/tooltip */
    private boolean shouldShowMeExistingPatternHints() {
        return context.disableExistingPatternExpansion() && computeRecipeQuantities;
    }

    private boolean shouldShowTopMaterialPatternHint(RequestedIngredient mat) {
        return shouldShowMeExistingPatternHints() && hasExistingPatternForRequestedIngredient(mat);
    }

    private boolean cachedTopMaterialShowsPatternHint(RequestedIngredient material) {
        for (TopMaterialRenderData data : topMaterialRenderData) {
            if (data.material() == material) {
                return data.showsPatternHint();
            }
        }
        return shouldShowTopMaterialPatternHint(material);
    }

    private @Nullable ITypedIngredient<?> resolveDisplayIngredient(RecipeTreeInputViewModel input) {
        ITypedIngredient<?> ingredient = input.displayIngredient();
        if (ingredient != null) {
            return ingredient;
        }
        ItemStack stack = input.displayStack();
        IIngredientManager ingredientManager = getIngredientManager();
        if (stack.isEmpty() || ingredientManager == null) {
            return null;
        }
        return ingredientManager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
    }

    private @Nullable ITypedIngredient<?> resolveGroupIngredient(List<RecipeTreeInputViewModel> inputs) {
        for (RecipeTreeInputViewModel input : inputs) {
            ITypedIngredient<?> ingredient = resolveDisplayIngredient(input);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
    }

    private void addMergedLeafChild(RecipeTreeNodeViewModel node, List<GraphNode> children,
            List<RecipeTreeInputViewModel> group, int totalRequiredAmount) {
        if (group.isEmpty()) {
            return;
        }
        RecipeTreeInputViewModel representative = group.get(0);
        MergedLeaf leaf = new MergedLeaf(resolveGroupIngredient(group), displayNameOf(representative), totalRequiredAmount,
                representative.amountText(), node, List.copyOf(group), List.of());
        String amountLabel = computeRecipeQuantities ? formatMergedLeafAmount(leaf) : "";
        String exact = computeRecipeQuantities ? exactAmountOf(leaf) : "";
        boolean showsPatternHint = shouldShowMeExistingPatternHints() && shouldBlockExpansion(leaf);
        children.add(new GraphNode(leaf.ingredient(), leaf.title(), amountLabel, exact, null, null, null, leaf,
                showsPatternHint, List.of(), computeNodeWidth(leaf.title(), amountLabel, false,
                        leaf.representative().hasAlternativeChoices(), false)));
    }

    private static int computeNodeWidth(String title, String amountLabel,
            boolean hasMachineIcon, boolean hasAlternativeButton, boolean hasCollapseButton) {
        int width = NODE_MAIN_SIZE;
        if (hasMachineIcon) {
            width += NODE_PART_GAP + MACHINE_SLOT_SIZE + NODE_PART_PADDING;
        }
        if (hasAlternativeButton) {
            width += NODE_PART_GAP + 10 + NODE_PART_PADDING;
        }
        if (hasCollapseButton) {
            width += NODE_PART_GAP + 10 + NODE_PART_PADDING;
        }
        return width;
    }

    private static String displayNameOf(RecipeTreeInputViewModel input) {
        String name = input.displayName();
        return name == null || name.isBlank()
                ? Component.translatable("gui.jeict.recipe_tree.unknown_input").getString()
                : name;
    }

    private static String formatMergedLeafAmount(MergedLeaf leaf) {
        return formatLayerMaterialAmountLabel(leaf.ingredient(), leaf.members(), leaf.totalAmount());
    }

    private static String exactAmountOf(MergedLeaf leaf) {
        String sourceText = leaf.sourceAmountText();
        if (isMilliBucketAmount(leaf.ingredient(), sourceText)) {
            return formatMilliBuckets(Math.max(1, leaf.totalAmount()));
        }
        if (sourceText != null && !sourceText.isBlank() && !sourceText.startsWith("x")) {
            return sourceText;
        }
        return Component.translatable("gui.jeict.recipe_tree.amount_exact", Math.max(1, leaf.totalAmount())).getString();
    }

    private static String formatLayerMaterialAmountLabel(@Nullable ITypedIngredient<?> ingredient,
            List<RecipeTreeInputViewModel> leafInputs, int totalAmount) {
        int safe = Math.max(1, totalAmount);
        String sampleText = leafInputs.isEmpty() ? "" : leafInputs.getFirst().amountText();
        if (isMilliBucketAmount(ingredient, sampleText)) {
            return formatMilliBuckets(safe);
        }
        if (sampleText != null && !sampleText.isBlank() && !sampleText.startsWith("x")) {
            return sampleText;
        }
        return formatCompactCount(safe);
    }

    private static boolean isMilliBucketAmount(@Nullable ITypedIngredient<?> ingredient, @Nullable String amountText) {
        if (amountText != null && amountText.contains("mB")) {
            return true;
        }
        if (ingredient == null) {
            return false;
        }
        if (ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).filter(stack -> !stack.isEmpty()).isPresent()) {
            return true;
        }
        return GenericIngredientUtil.tryGetMekanismChemicalAmount(ingredient.getIngredient()) > 0L;
    }

    private static String formatMilliBuckets(int amount) {
        int safe = Math.max(1, amount);
        if (safe < 1000) {
            return safe + " mB";
        }
        String buckets = java.math.BigDecimal.valueOf(safe, 3).stripTrailingZeros().toPlainString();
        return buckets + " B";
    }

    private String leafSignatureOf(RecipeTreeInputViewModel input) {
        return signatureOf(input);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty: super.render() calls this internally again,
        // so we tile the background manually at the start of render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long startedAt = RecipeTreePerfDebug.begin();
        lastRenderedNodeCount = 0;
        lastRenderedEdgeCount = 0;
        lastRenderedLayerCount = 0;
        lastRenderedLayerMaterialCount = 0;
        lastRenderedTopMaterialCount = 0;
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        graphics.drawManaged(() -> renderWorkspaceBackdrop(graphics, theme));

        int footerHeight = currentFooterHeight();
        boolean clippedWorkspace = this.width > 12 && this.height > HEADER_HEIGHT + footerHeight + 2;
        updateVisibleLogicalBounds(clippedWorkspace);
        if (clippedWorkspace) {
            graphics.enableScissor(canvasLeft() + 2, HEADER_HEIGHT + 2, canvasRight() - 2, this.height - footerHeight - 2);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);
        if (autoMergeSameMaterials) {
            graphics.drawManaged(() -> renderMergedLayerEdges(graphics));
            renderMergedLayers(graphics);
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            renderTopMaterialsMerged(graphics);
        } else {
            graphics.drawManaged(() -> renderEdges(graphics));
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            renderTopMaterials(graphics);
            renderNodes(graphics);
        }
        renderBatchText(graphics, theme);
        graphics.pose().popPose();
        if (clippedWorkspace) {
            graphics.disableScissor();
            renderWorkspaceFrame(graphics, theme);
        }

        renderOverlayPanelBackdrops(graphics, theme);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderAe2WidgetChrome(graphics, mouseX, mouseY);
        renderDockPanels(graphics, theme, mouseX, mouseY);
        int headerTextRight = Math.max(72, toolbarLeft - 8);
        graphics.enableScissor(64, 5, headerTextRight, HEADER_HEIGHT - 2);
        graphics.drawString(this.font, context.root().recipe().title(), 66, 8, theme.titleText(), false);
        graphics.drawString(this.font, cachedRequiredPatternsTitleLine, 66, 21, theme.metricText(), false);
        graphics.disableScissor();
        String zoomLabel = Math.round(zoom * 100.0D) + "%";
        graphics.drawCenteredString(this.font, zoomLabel, this.width - 106, 14, theme.metricText());
        renderFooterStatus(graphics, theme);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 320.0F);
        renderPatternSubstitutionToggles(graphics);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        renderAlternativeSelection(graphics, mouseX, mouseY);
        graphics.pose().popPose();

        double logicalMouseX = (mouseX - panX) / zoom;
        double logicalMouseY = (mouseY - panY) / zoom;
        renderTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
        if (RecipeTreePerfDebug.isEnabled()) {
            RecipeTreePerfDebug.logRenderSummary(startedAt,
                    "merge={} qty={} totalNodes={} visibleNodes={} totalEdges={} visibleEdges={} rows={} visibleRows={} visibleLayerMaterials={} topMaterialsVisible={} zoom={}",
                    autoMergeSameMaterials, computeRecipeQuantities,
                    positionedNodes.size(), lastRenderedNodeCount,
                    edges.size(), lastRenderedEdgeCount,
                    mergedLayerRows.size(), lastRenderedLayerCount, lastRenderedLayerMaterialCount,
                    lastRenderedTopMaterialCount, zoom);
        }
    }

    /** MEST-style floating AE2 terminal shell with a restrained content well. */
    private int canvasLeft() {
        return settingsOpen ? SETTINGS_WIDTH + 8 : SETTINGS_COLLAPSED_WIDTH + 6;
    }

    private int canvasRight() {
        return hasInspectorSelection() && this.width >= 520 ? this.width - INSPECTOR_WIDTH - 8 : this.width - 6;
    }

    private boolean hasInspectorSelection() {
        return selectedNode != null || selectedLayerMaterial != null;
    }

    private void renderOverlayPanelBackdrops(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        if (hasInspectorSelection() && this.width >= 520) {
            RecipeTreeTheme.drawFramedPanel(graphics, canvasRight() + 6, HEADER_HEIGHT + 4, this.width - 6,
                    this.height - currentFooterHeight() - 4);
        }
        RecipeTreeTheme.drawRaisedPanel(graphics, 4, HEADER_HEIGHT + 2, canvasLeft() - 2,
                this.height - currentFooterHeight() - 2);
    }

    private void renderDockPanels(GuiGraphics graphics, RecipeTreeTheme.Palette theme, int mouseX, int mouseY) {
        renderInspectorPanel(graphics, theme);
    }

    private void renderInspectorPanel(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        if (!hasInspectorSelection() || this.width < 520) {
            return;
        }
        int left = canvasRight() + 16;
        int right = this.width - 16;
        int top = HEADER_HEIGHT + 14;
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.inspector_title"), left, top, theme.titleText(), false);
        String label;
        String amount;
        @Nullable ITypedIngredient<?> ingredient;
        @Nullable Component machineName;
        boolean recipeNode;
        boolean patternHint;
        if (selectedNode != null) {
            GraphNode graph = selectedNode.graph();
            label = graph.title();
            amount = graph.exactAmount().isBlank() ? graph.amount() : graph.exactAmount();
            ingredient = graph.ingredient();
            machineName = graph.machineName();
            recipeNode = graph.recipeNode() != null;
            patternHint = graph.showsPatternHint();
        } else {
            LayerMaterial material = selectedLayerMaterial;
            label = material.label();
            amount = computeRecipeQuantities
                    ? Component.translatable("gui.jeict.recipe_tree.amount_exact", material.totalAmount()).getString()
                    : material.amountLabel();
            ingredient = material.ingredient();
            machineName = material.machineName();
            recipeNode = !material.recipeTargets().isEmpty();
            patternHint = material.showsPatternHint();
        }
        RecipeTreeTheme.drawSlot(graphics, left, top + 20);
        renderIngredientAt(graphics, ingredient, left + 1, top + 21);
        graphics.drawString(this.font, trimToWidth(Component.literal(label), Math.max(20, right - left - 28)),
                left + 26, top + 23, theme.titleText(), false);
        int textY = top + 50;
        if (amount != null && !amount.isBlank()) {
            graphics.drawString(this.font, Component.literal(amount), left, textY, theme.metricText(), false);
            textY += 15;
        }
        graphics.drawString(this.font, Component.translatable(recipeNode
                ? "gui.jeict.recipe_tree.inspector_recipe"
                : "gui.jeict.recipe_tree.inspector_material"), left, textY, theme.mutedText(), false);
        textY += 15;
        if (machineName != null) {
            graphics.drawString(this.font, trimToWidth(machineName, right - left), left, textY, theme.hintText(), false);
            textY += 15;
        }
        if (patternHint) {
            graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.pattern_exists"),
                    left, textY, theme.success(), false);
            textY += 15;
        }
        graphics.fill(left, textY + 3, right, textY + 4, theme.gridMajorLine());
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.inspector_action_hint"),
                left, textY + 12, theme.hintText(), false);
    }

    private void renderFooterStatus(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        Component summary = Component.translatable("gui.jeict.recipe_tree.footer_summary",
                topMaterials.size() + genericTopMaterialRenderData.size(), cachedMissingMaterialCount);
        graphics.drawString(this.font, summary, canvasLeft() + 6, this.height - 21, theme.metricText(), false);
    }

    private void renderWorkspaceBackdrop(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        graphics.fill(0, 0, this.width, this.height, theme.backgroundOverlay());
        if (this.width <= 10 || this.height <= 10) {
            graphics.fill(0, 0, this.width, this.height, theme.chromeFill());
            return;
        }

        // MEST panels float over the world with a compact two-pixel shadow.
        graphics.fill(7, 7, this.width - 1, this.height - 1, 0x66000000);
        graphics.fill(this.width - 7, 7, this.width - 3, this.height - 3, 0x66000000);
        RecipeTreeTheme.drawRaisedPanel(graphics, 3, 3, this.width - 5, this.height - 5);

        // AE2 headers remain part of the light dialog surface, separated by two hairlines.
        graphics.fill(8, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, theme.raisedShadow());
        graphics.fill(8, HEADER_HEIGHT - 1, this.width - 10, HEADER_HEIGHT, theme.raisedHighlight());
        int footerHeight = currentFooterHeight();
        graphics.fill(8, this.height - footerHeight, this.width - 10,
                this.height - footerHeight + 1, theme.raisedShadow());
        graphics.fill(8, this.height - footerHeight + 1, this.width - 10,
                this.height - footerHeight + 2, theme.raisedHighlight());

        int wellLeft = canvasLeft();
        int wellTop = HEADER_HEIGHT;
        int wellRight = canvasRight();
        int wellBottom = this.height - footerHeight;
        if (wellRight > wellLeft + 2 && wellBottom > wellTop + 2) {
            RecipeTreeTheme.drawSunkenPanel(graphics, wellLeft, wellTop, wellRight, wellBottom);
        }
    }

    private void renderWorkspaceFrame(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        // Re-stroke the well rim so clipped graph content never paints over it.
        int left = canvasLeft();
        int top = HEADER_HEIGHT;
        int right = canvasRight();
        int bottom = this.height - currentFooterHeight();
        RecipeTreeTheme.drawBorder(graphics, left, top, right, bottom, theme.chromeBorder());
        if (right - left > 2 && bottom - top > 2) {
            graphics.fill(left + 1, top + 1, right - 1, top + 2, theme.raisedShadow());
            graphics.fill(left + 1, top + 1, left + 2, bottom - 1, theme.raisedShadow());
            graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, theme.raisedHighlight());
            graphics.fill(right - 2, top + 1, right - 1, bottom - 1, theme.raisedHighlight());
        }
    }


    private boolean isInsideWorkspace(double mouseX, double mouseY) {
        return mouseX >= canvasLeft() + 2 && mouseX < canvasRight() - 2
                && mouseY >= HEADER_HEIGHT + 2 && mouseY < this.height - currentFooterHeight() - 2;
    }

    private int currentFooterHeight() {
        boolean hasActions = (encodeButton != null && encodeButton.visible)
                || (uploadButton != null && uploadButton.visible);
        return hasActions ? FOOTER_HEIGHT : 8;
    }

    /**
     * Vanilla widgets stay for focus/narration/hit-testing only (they render
     * nothing). This paints AE2 terminal buttons on top.
     */
    private void renderAe2WidgetChrome(GuiGraphics graphics, int mouseX, int mouseY) {
        renderAe2Button(graphics, backButton, mouseX, mouseY);
        renderAe2Button(graphics, computeQuantitiesButton, mouseX, mouseY);
        renderAe2Button(graphics, styleButton, mouseX, mouseY);
        renderAe2Button(graphics, toggleExistingPatternButton, mouseX, mouseY);
        renderAe2Button(graphics, autoUniqueRecipeButton, mouseX, mouseY);
        renderAe2Button(graphics, memoryReadingButton, mouseX, mouseY);
        renderAe2Button(graphics, autoMergeButton, mouseX, mouseY);
        renderAe2Button(graphics, zoomOutButton, mouseX, mouseY);
        renderAe2Button(graphics, zoomInButton, mouseX, mouseY);
        renderAe2Button(graphics, fitViewButton, mouseX, mouseY);
        renderAe2Button(graphics, settingsButton, mouseX, mouseY);
        renderAe2Button(graphics, encodeButton, mouseX, mouseY);
        renderAe2Button(graphics, uploadButton, mouseX, mouseY);
    }



    private void renderBatchText(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        RootBatchAnchor anchor = updateBatchBadgeBounds();
        if (anchor == null) {
            return;
        }
        int x = anchor.right() + BATCH_TEXT_GAP;
        int y = anchor.y() + Math.max(0, (NODE_HEIGHT - this.font.lineHeight) / 2);
        graphics.drawString(this.font, batchCountLabel(), x, y, theme.hintText(), false);
    }

    private void renderAe2Button(GuiGraphics graphics, @Nullable Button button, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return;
        }
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean hovered = button.active && (button.isFocused()
                || (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height));
        RecipeTreeTheme.drawButton(graphics, x, y, width, height, hovered, button.active);
        if (button == settingsButton || (!settingsOpen && isSidebarControlButton(button))) {
            renderSidebarIcon(graphics, button, x + (width - 8) / 2, y + (height - 8) / 2, hovered);
            return;
        }
        int textColor = button.active ? (hovered ? theme.controlHoverText() : theme.controlText()) : theme.mutedText();
        Component message = button.getMessage();
        int textX = x + Math.max(2, (width - this.font.width(message)) / 2);
        int textY = y + (height - this.font.lineHeight) / 2;
        graphics.drawString(this.font, message, textX, textY, textColor, false);
    }

    private boolean isSidebarControlButton(Button button) {
        return button == autoMergeButton || button == computeQuantitiesButton || button == autoUniqueRecipeButton
                || button == memoryReadingButton || button == toggleExistingPatternButton || button == styleButton;
    }

    private void renderSidebarIcon(GuiGraphics graphics, Button button, int x, int y, boolean hovered) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        boolean enabled = sidebarControlEnabled(button);
        int color = hovered ? theme.controlHoverText() : (enabled ? theme.accent() : theme.mutedText());
        if (button == settingsButton) {
            if (settingsOpen) {
                graphics.fill(x + 2, y + 1, x + 3, y + 7, color);
                graphics.fill(x + 3, y + 2, x + 4, y + 6, color);
                graphics.fill(x + 4, y + 3, x + 5, y + 5, color);
            } else {
                graphics.fill(x + 5, y + 1, x + 6, y + 7, color);
                graphics.fill(x + 4, y + 2, x + 5, y + 6, color);
                graphics.fill(x + 3, y + 3, x + 4, y + 5, color);
            }
            return;
        }
        if (button == autoMergeButton) {
            graphics.fill(x, y + 1, x + 3, y + 2, color);
            graphics.fill(x, y + 6, x + 3, y + 7, color);
            graphics.fill(x + 2, y + 2, x + 4, y + 6, color);
            graphics.fill(x + 4, y + 3, x + 7, y + 5, color);
            graphics.fill(x + 6, y + 2, x + 8, y + 6, color);
        } else if (button == computeQuantitiesButton) {
            graphics.fill(x + 1, y + 5, x + 2, y + 8, color);
            graphics.fill(x + 3, y + 3, x + 4, y + 8, color);
            graphics.fill(x + 5, y + 1, x + 6, y + 8, color);
        } else if (button == autoUniqueRecipeButton) {
            graphics.fill(x + 1, y + 1, x + 2, y + 7, color);
            graphics.fill(x + 2, y + 3, x + 5, y + 4, color);
            graphics.fill(x + 5, y + 2, x + 7, y + 5, color);
        } else if (button == memoryReadingButton) {
            RecipeTreeTheme.drawBorder(graphics, x + 1, y + 1, x + 7, y + 7, color);
            graphics.fill(x + 3, y + 3, x + 5, y + 5, color);
        } else if (button == toggleExistingPatternButton) {
            RecipeTreeTheme.drawBorder(graphics, x + 1, y, x + 7, y + 8, color);
            graphics.fill(x + 3, y + 2, x + 6, y + 3, color);
            graphics.fill(x + 3, y + 4, x + 6, y + 5, color);
        } else if (button == styleButton) {
            graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
            graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
            graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
            graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
        }
        if (!enabled && button != styleButton) {
            int slash = hovered ? theme.danger() : theme.controlText();
            for (int offset = 0; offset < 6; offset++) {
                graphics.fill(x + 1 + offset, y + 6 - offset, x + 2 + offset, y + 7 - offset, slash);
            }
        }
    }

    private boolean sidebarControlEnabled(Button button) {
        if (button == autoMergeButton) {
            return autoMergeSameMaterials;
        }
        if (button == computeQuantitiesButton) {
            return computeRecipeQuantities;
        }
        if (button == autoUniqueRecipeButton) {
            return autoExpandUniqueEncodableRecipe;
        }
        if (button == memoryReadingButton) {
            return readRememberedSelections;
        }
        if (button == toggleExistingPatternButton) {
            return !context.disableExistingPatternExpansion();
        }
        return true;
    }

    private void renderEdges(GuiGraphics graphics) {
        if (edgeRows.isEmpty()) {
            return;
        }
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        int minRowY = (int) Math.floor(visibleLogicalMinY - LEVEL_GAP - NODE_HEIGHT);
        int maxRowY = (int) Math.ceil(visibleLogicalMaxY);
        for (List<Edge> row : edgeRows.subMap(minRowY, true, maxRowY, true).values()) {
            for (Edge edge : row) {
                if (edgeMinX(edge) > visibleLogicalMaxX) {
                    break;
                }
                if (edgeMaxX(edge) < visibleLogicalMinX) {
                    continue;
                }
                int startX = edge.parent().x() + edge.parent().graph().width() / 2;
                int startY = edge.parent().y() + NODE_HEIGHT;
                int endX = edge.child().x() + edge.child().graph().width() / 2;
                int endY = edge.child().y();
                lastRenderedEdgeCount++;
                int midY = startY + Math.max(1, (endY - startY) / 2);
                graphics.fill(startX, startY, startX + 1, midY, theme.edge());
                graphics.fill(Math.min(startX, endX), midY, Math.max(startX, endX) + 1, midY + 1, theme.edge());
                graphics.fill(endX, midY, endX + 1, endY, theme.edge());
            }
        }
    }

    private void renderNodes(GuiGraphics graphics) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        boolean detailed = isDetailedNodeRendering();
        int lowZoomIconGap = detailed ? 0 : Math.max(NODE_MAIN_SIZE, (int) Math.ceil(14.0D / zoom));
        alternativeButtonBounds.clear();
        visiblePositionedNodes.clear();
        if (positionedNodeRows.isEmpty()) {
            return;
        }
        int minRowY = (int) Math.floor(visibleLogicalMinY - NODE_HEIGHT);
        int maxRowY = (int) Math.ceil(visibleLogicalMaxY);
        for (List<PositionedNode> row : positionedNodeRows.subMap(minRowY, true, maxRowY, true).values()) {
            int lastIconX = Integer.MIN_VALUE / 2;
            for (PositionedNode node : row) {
                int x = node.x();
                int y = node.y();
                int width = node.graph().width();
                if (x > visibleLogicalMaxX) {
                    break;
                }
                if (x + width < visibleLogicalMinX) {
                    continue;
                }
                visiblePositionedNodes.add(node);
                lastRenderedNodeCount++;
                int accent = node.graph().showsPatternHint()
                        ? theme.patternHintBorder()
                        : (node.graph().recipeNode() != null ? theme.controlHoverText() : theme.mutedText());
                RecipeTreeTheme.drawMarkdownNode(graphics, x, y, x + width, y + NODE_HEIGHT, accent);
                if (node == selectedNode) {
                    RecipeTreeTheme.drawBorder(graphics, x - 1, y - 1, x + width + 1, y + NODE_HEIGHT + 1, theme.accent());
                }
                boolean genericIngredient = node.graph().ingredient() != null
                        && extractItemStack(node.graph().ingredient()).isEmpty();
                boolean renderIcons = genericIngredient || detailed || x - lastIconX >= lowZoomIconGap;
                if (renderIcons) {
                    renderIngredientAt(graphics, node.graph().ingredient(), x + 6, y + 6);
                    lastIconX = x;
                }
                if (detailed) {
                    renderNodeAmount(graphics, node.graph().amount(), x, y);
                }
                int partX = x + NODE_MAIN_SIZE + NODE_PART_GAP;
                if (node.graph().machineIcon() != null) {
                    if (renderIcons) {
                        renderMachineSlot(graphics, node.graph().machineIcon(), partX, y + 5);
                    }
                    partX += MACHINE_SLOT_SIZE + NODE_PART_GAP;
                }
                if (node.graph().mergedLeaf() != null && node.graph().mergedLeaf().representative().hasAlternativeChoices()) {
                    int buttonX = partX;
                    int buttonY = y + 9;
                    renderAlternativeButton(graphics, buttonX, buttonY);
                    alternativeButtonBounds.add(AlternativeButtonBounds.forLeaf(node.graph().mergedLeaf(), buttonX, buttonY, 10, 10));
                }
                if (showsCollapseButton(node)) {
                    int btnX = x + width - NODE_PART_PADDING - 10;
                    int btnY = y + 9;
                    renderSmallControlIcon(graphics, "-", btnX, btnY, 10);
                }
            }
        }
    }

    private int mergedRowX(int startX, LayerRowRenderCache cache) {
        return startX + cachedMergedAnchorOffset - cache.anchorCenterOffset();
    }

    private static int lowerBound(int[] values, int target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (values[mid] < target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private List<Integer> visibleLayerMaterialCenters(LayerRow row, int rowX) {
        LayerRowRenderCache cache = mergedRowRenderCaches.get(row);
        if (cache == null || cache.centerOffsets().length == 0) {
            return List.of();
        }
        int minOffset = (int) Math.floor(visibleLogicalMinX - rowX - NODE_MAIN_SIZE);
        int maxOffset = (int) Math.ceil(visibleLogicalMaxX - rowX + NODE_MAIN_SIZE);
        int start = lowerBound(cache.centerOffsets(), minOffset);
        List<Integer> centers = new ArrayList<>();
        for (int i = start; i < cache.centerOffsets().length; i++) {
            int offset = cache.centerOffsets()[i];
            if (offset > maxOffset) {
                break;
            }
            centers.add(rowX + offset);
        }
        return centers;
    }

    private void renderMergedLayerEdges(GuiGraphics graphics) {
        if (mergedLayerRows.size() < 2) {
            return;
        }
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        int startX = 36;
        int baseY = 42 + TOP_MATERIALS_OFFSET;
        int firstEdge = Math.max(0, (int) Math.floor((visibleLogicalMinY - baseY) / LEVEL_GAP) - 1);
        int lastEdge = Math.min(mergedLayerRows.size() - 2,
                (int) Math.floor((visibleLogicalMaxY - baseY) / LEVEL_GAP));
        if (firstEdge > lastEdge) {
            return;
        }
        for (int idx = firstEdge; idx <= lastEdge; idx++) {
            LayerRow rowA = mergedLayerRows.get(idx);
            LayerRow rowB = mergedLayerRows.get(idx + 1);
            LayerRowRenderCache rowCacheA = mergedRowRenderCaches.get(rowA);
            LayerRowRenderCache rowCacheB = mergedRowRenderCaches.get(rowB);
            if (rowCacheA == null || rowCacheB == null) {
                continue;
            }
            int rowAX = mergedRowX(startX, rowCacheA);
            int rowBX = mergedRowX(startX, rowCacheB);
            int startY = baseY + idx * LEVEL_GAP + NODE_HEIGHT;
            int endY = baseY + (idx + 1) * LEVEL_GAP;
            int midY = startY + Math.max(1, (endY - startY) / 2);
            List<Integer> parentCenters = visibleLayerMaterialCenters(rowA, rowAX);
            List<Integer> childCenters = visibleLayerMaterialCenters(rowB, rowBX);
            if (parentCenters.isEmpty() || childCenters.isEmpty()) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (int center : parentCenters) {
                minX = Math.min(minX, center);
                maxX = Math.max(maxX, center);
            }
            for (int center : childCenters) {
                minX = Math.min(minX, center);
                maxX = Math.max(maxX, center);
            }
            if (!isLogicalRectVisible(minX - 1, startY, maxX + 1, endY)) {
                continue;
            }
            for (int center : parentCenters) {
                graphics.fill(center, startY, center + 1, midY + 1, theme.edge());
            }
            graphics.fill(minX, midY, maxX + 1, midY + 1, theme.edge());
            for (int center : childCenters) {
                graphics.fill(center, midY, center + 1, endY, theme.edge());
            }
            lastRenderedEdgeCount += parentCenters.size() + childCenters.size();
        }
    }

    private void renderMergedLayers(GuiGraphics graphics) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        boolean detailed = isDetailedNodeRendering();
        int lowZoomIconGap = detailed ? 0 : Math.max(NODE_MAIN_SIZE, (int) Math.ceil(14.0D / zoom));
        layerMaterialBounds.clear();
        alternativeButtonBounds.clear();
        if (mergedLayerRows.isEmpty()) {
            return;
        }
        int startX = 36;
        int y = 42 + TOP_MATERIALS_OFFSET;
        int firstRow = Math.max(0,
                (int) Math.ceil((visibleLogicalMinY - y - NODE_HEIGHT) / LEVEL_GAP));
        int lastRow = Math.min(mergedLayerRows.size() - 1,
                (int) Math.floor((visibleLogicalMaxY - y) / LEVEL_GAP));
        if (firstRow > lastRow) {
            return;
        }
        for (int i = firstRow; i <= lastRow; i++) {
            LayerRow row = mergedLayerRows.get(i);
            LayerRowRenderCache rowCache = mergedRowRenderCaches.get(row);
            if (rowCache == null) {
                continue;
            }
            int rowX = mergedRowX(startX, rowCache);
            int rowY = y + i * LEVEL_GAP;
            if (!isLogicalRectVisible(rowX, rowY, rowX + rowCache.width(), rowY + NODE_HEIGHT)) {
                continue;
            }
            lastRenderedLayerCount++;
            int relativeMinX = (int) Math.floor(visibleLogicalMinX - rowX);
            int startIndex = lowerBound(rowCache.rightOffsets(), relativeMinX);
            int lastIconX = Integer.MIN_VALUE / 2;
            for (int materialIndex = startIndex; materialIndex < row.materials().size(); materialIndex++) {
                LayerMaterial material = row.materials().get(materialIndex);
                int currentX = rowX + rowCache.leftOffsets()[materialIndex];
                int materialWidth = material.width();
                if (currentX > visibleLogicalMaxX) {
                    break;
                }
                if (!isLogicalRectVisible(currentX, rowY, currentX + materialWidth, rowY + NODE_HEIGHT)) {
                    continue;
                }
                lastRenderedLayerMaterialCount++;
                int accent = material.showsPatternHint()
                        ? theme.patternHintBorder()
                        : (!material.recipeTargets().isEmpty() ? theme.controlHoverText() : theme.mutedText());
                RecipeTreeTheme.drawMarkdownNode(graphics, currentX, rowY,
                        currentX + materialWidth, rowY + NODE_HEIGHT, accent);
                if (material == selectedLayerMaterial) {
                    RecipeTreeTheme.drawBorder(graphics, currentX - 1, rowY - 1,
                            currentX + materialWidth + 1, rowY + NODE_HEIGHT + 1, theme.accent());
                }
                boolean renderIcons = detailed || currentX - lastIconX >= lowZoomIconGap;
                if (renderIcons) {
                    renderIngredientAt(graphics, material.ingredient(), currentX + 6, rowY + 6);
                    lastIconX = currentX;
                }
                if (detailed) {
                    renderNodeAmount(graphics, material.amountLabel(), currentX, rowY);
                }
                int partX = currentX + NODE_MAIN_SIZE + NODE_PART_GAP;
                if (material.machineIcon() != null) {
                    if (renderIcons) {
                        renderMachineSlot(graphics, material.machineIcon(), partX, rowY + 5);
                    }
                    partX += MACHINE_SLOT_SIZE + NODE_PART_GAP;
                }
                if (detailed && material.hasAlternatives() && material.hasUnresolvedLeaves()) {
                    MergedLeaf altLeaf = material.leafProjection();
                    if (altLeaf != null) {
                        int buttonX = partX;
                        int buttonY = rowY + 9;
                        renderAlternativeButton(graphics, buttonX, buttonY);
                        alternativeButtonBounds.add(AlternativeButtonBounds.forLeaf(altLeaf, buttonX, buttonY, 10, 10));
                    }
                }
                if (detailed) {
                    layerMaterialBounds.add(new LayerMaterialBounds(row, material, currentX, rowY, materialWidth, NODE_HEIGHT));
                }
            }
        }
    }

    private void renderTopMaterialsMerged(GuiGraphics graphics) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        topMaterialBounds.clear();
        surplusMaterialBounds.clear();
        genericTopMaterialBounds.clear();
        topMaterialsPinButtonBounds = null;
        if ((topMaterialRenderData.isEmpty() && genericTopMaterialRenderData.isEmpty()) || mergedLayerRows.isEmpty()) {
            return;
        }
        LayerRow rootRow = mergedLayerRows.getFirst();
        LayerRowRenderCache rootRowCache = mergedRowRenderCaches.get(rootRow);
        if (rootRowCache == null) {
            return;
        }
        int contentWidth = computeMergedLayerContentWidth();
        int startX = 36;
        int rowX = mergedRowX(startX, rootRowCache);
        int rootY = 42 + TOP_MATERIALS_OFFSET;
        int rootCenterX = rowX + rootRowCache.anchorCenterOffset();

        int gap = 4;
        int totalWidth = -gap;
        for (TopMaterialRenderData data : topMaterialRenderData) {
            totalWidth += data.width() + gap;
        }
        for (GenericTopMaterialRenderData data : genericTopMaterialRenderData) {
            totalWidth += data.width() + gap;
        }
        if (totalWidth < 0) {
            return;
        }

        int materialsStartX = rootCenterX - totalWidth / 2;
        int surplusWidth = totalMaterialRowWidth(surplusMaterialRenderData, gap);
        int surplusStartX = rootCenterX - surplusWidth / 2;
        int panelY = rootY - NODE_HEIGHT * 2 - 28;
        int surplusY = panelY + NODE_HEIGHT + 16;
        int pinX = materialsStartX + totalWidth + 8;
        int visibleLeft = surplusWidth > 0 ? Math.min(materialsStartX, surplusStartX) : materialsStartX;
        int visibleRight = surplusWidth > 0 ? Math.max(pinX + 12, surplusStartX + surplusWidth) : pinX + 12;
        int visibleBottom = surplusWidth > 0 ? surplusY + NODE_HEIGHT + 4 : panelY + NODE_HEIGHT + 4;
        if (!isLogicalRectVisible(visibleLeft - 4, panelY - 14, visibleRight, visibleBottom)) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.total_materials"),
                materialsStartX, panelY - 12, theme.hintText(), false);
        renderTopMaterialsPinButton(graphics, pinX, panelY + 7);

        int currentX = materialsStartX;
        for (TopMaterialRenderData data : topMaterialRenderData) {
            RequestedIngredient mat = data.material();
            int width = data.width();
            if (width <= 0) {
                continue;
            }
            if (currentX + width >= visibleLogicalMinX && currentX <= visibleLogicalMaxX) {
                int accent = data.showsPatternHint() ? theme.patternHintBorder() : theme.controlHoverText();
                renderMaterialDemandSlot(graphics, data, currentX, panelY, width, accent);
                topMaterialBounds.add(new TopMaterialBounds(mat, currentX, panelY, width, NODE_HEIGHT));
                if (mat.alternatives().size() > 1) {
                    int buttonX = currentX + width - 13;
                    int buttonY = panelY + 8;
                    renderAlternativeButton(graphics, buttonX, buttonY);
                    alternativeButtonBounds.add(AlternativeButtonBounds.forMaterial(mat, buttonX, buttonY, 10, 10));
                }
            }
            currentX += width + gap;
        }
        renderGenericTopMaterialRow(graphics, theme, currentX, panelY, gap);
        renderSurplusMaterialRow(graphics, theme, surplusStartX, surplusY, gap);
    }

    private void renderTopMaterials(GuiGraphics graphics) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        topMaterialBounds.clear();
        surplusMaterialBounds.clear();
        genericTopMaterialBounds.clear();
        topMaterialsPinButtonBounds = null;
        if ((topMaterialRenderData.isEmpty() && genericTopMaterialRenderData.isEmpty()) || rootNode == null) {
            return;
        }

        int rootCenterX = 0;
        int rootY = 0;
        for (PositionedNode node : positionedNodes) {
            if (node.graph() == rootNode) {
                rootCenterX = node.x() + node.graph().width() / 2;
                rootY = node.y();
                break;
            }
        }

        int gap = 4;
        int totalWidth = -gap;
        for (TopMaterialRenderData data : topMaterialRenderData) {
            totalWidth += data.width() + gap;
        }
        for (GenericTopMaterialRenderData data : genericTopMaterialRenderData) {
            totalWidth += data.width() + gap;
        }
        if (totalWidth < 0) {
            return;
        }

        int startX = rootCenterX - totalWidth / 2;
        int surplusWidth = totalMaterialRowWidth(surplusMaterialRenderData, gap);
        int surplusStartX = rootCenterX - surplusWidth / 2;
        int y = rootY - NODE_HEIGHT * 2 - 28;
        int surplusY = y + NODE_HEIGHT + 16;
        int pinX = startX + totalWidth + 8;
        int visibleLeft = surplusWidth > 0 ? Math.min(startX, surplusStartX) : startX;
        int visibleRight = surplusWidth > 0 ? Math.max(pinX + 12, surplusStartX + surplusWidth) : pinX + 12;
        int visibleBottom = surplusWidth > 0 ? surplusY + NODE_HEIGHT + 4 : y + NODE_HEIGHT + 4;
        if (!isLogicalRectVisible(visibleLeft - 4, y - 14, visibleRight, visibleBottom)) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.total_materials"),
                startX, y - 12, theme.hintText(), false);
        renderTopMaterialsPinButton(graphics, pinX, y + 7);

        int currentX = startX;
        for (TopMaterialRenderData data : topMaterialRenderData) {
            RequestedIngredient mat = data.material();
            int width = data.width();
            if (width <= 0) {
                continue;
            }
            if (currentX + width >= visibleLogicalMinX && currentX <= visibleLogicalMaxX) {
                int accent = data.showsPatternHint() ? theme.patternHintBorder() : theme.controlHoverText();
                renderMaterialDemandSlot(graphics, data, currentX, y, width, accent);
                topMaterialBounds.add(new TopMaterialBounds(mat, currentX, y, width, NODE_HEIGHT));
                if (mat.alternatives().size() > 1) {
                    int buttonX = currentX + width - 13;
                    int buttonY = y + 8;
                    renderAlternativeButton(graphics, buttonX, buttonY);
                    alternativeButtonBounds.add(AlternativeButtonBounds.forMaterial(mat, buttonX, buttonY, 10, 10));
                }
            }
            currentX += width + gap;
        }
        renderGenericTopMaterialRow(graphics, theme, currentX, y, gap);
        renderSurplusMaterialRow(graphics, theme, surplusStartX, surplusY, gap);
    }

    private void renderGenericTopMaterialRow(GuiGraphics graphics, RecipeTreeTheme.Palette theme,
            int startX, int y, int gap) {
        int currentX = startX;
        for (GenericTopMaterialRenderData data : genericTopMaterialRenderData) {
            MergedLeaf leaf = data.leaf();
            int width = data.width();
            if (currentX + width >= visibleLogicalMinX && currentX <= visibleLogicalMaxX) {
                RecipeTreeTheme.drawMarkdownNode(graphics, currentX, y, currentX + width, y + NODE_HEIGHT,
                        theme.controlHoverText());
                RecipeTreeTheme.drawSlot(graphics, currentX + 5, y + 5);
                renderIngredientAt(graphics, leaf.ingredient(), currentX + 6, y + 6);
                if (!data.label().isBlank()) {
                    graphics.drawString(this.font, data.label(), currentX + 28, y + 9, theme.metricText(), false);
                }
                genericTopMaterialBounds.add(new GenericTopMaterialBounds(data, currentX, y, width, NODE_HEIGHT));
                if (leaf.representative().hasAlternativeChoices()) {
                    int buttonX = currentX + width - 13;
                    int buttonY = y + 8;
                    renderAlternativeButton(graphics, buttonX, buttonY);
                    alternativeButtonBounds.add(AlternativeButtonBounds.forLeaf(leaf, buttonX, buttonY, 10, 10));
                }
            }
            currentX += width + gap;
        }
    }

    private int totalMaterialRowWidth(List<TopMaterialRenderData> dataList, int gap) {
        if (dataList.isEmpty()) {
            return 0;
        }
        int width = -gap;
        for (TopMaterialRenderData data : dataList) {
            width += data.width() + gap;
        }
        return Math.max(0, width);
    }

    private void renderSurplusMaterialRow(GuiGraphics graphics, RecipeTreeTheme.Palette theme,
            int startX, int y, int gap) {
        if (surplusMaterialRenderData.isEmpty()) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.surplus_materials"),
                startX, y - 10, theme.hintText(), false);
        int currentX = startX;
        for (TopMaterialRenderData data : surplusMaterialRenderData) {
            int width = data.width();
            if (currentX + width >= visibleLogicalMinX && currentX <= visibleLogicalMaxX) {
                renderMaterialDemandSlot(graphics, data, currentX, y, width, theme.enough());
                surplusMaterialBounds.add(new TopMaterialBounds(data.material(), currentX, y, width, NODE_HEIGHT));
            }
            currentX += width + gap;
        }
    }

    /** AE2 terminal-style material demand slot: recessed 18x18 item well and compact count label. */
    private void renderMaterialDemandSlot(GuiGraphics graphics, TopMaterialRenderData data,
            int x, int y, int width, int accent) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawMarkdownNode(graphics, x, y, x + width, y + NODE_HEIGHT, accent);
        int slotX = x + 5;
        int slotY = y + 5;
        RecipeTreeTheme.drawSlot(graphics, slotX, slotY);
        ItemStack itemStack = data.stack();
        if (!itemStack.isEmpty()) {
            lastRenderedTopMaterialCount++;
            graphics.renderItem(itemStack, slotX + 1, slotY + 1);
        }
        if (!data.label().isEmpty()) {
            graphics.drawString(this.font, data.label(), slotX + 23, y + 9, data.labelColor(), false);
        }
        if (data.showsPatternHint()) {
            graphics.fill(x + 1, y + NODE_HEIGHT - 3, x + width - 1, y + NODE_HEIGHT - 1, theme.patternHintBorder());
        }
    }

    private void renderTopMaterialsPinButton(GuiGraphics graphics, int x, int y) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        renderSmallControlIcon(graphics, "+", x, y, 12);
        topMaterialsPinButtonBounds = new TopMaterialsPinButtonBounds(x, y, 12, 12);
    }

    private void renderAlternativeButton(GuiGraphics graphics, int x, int y) {
        renderSmallControlIcon(graphics, "v", x, y, 10);
    }

    private void renderSmallControlIcon(GuiGraphics graphics, String symbol, int x, int y, int size) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawSmallControl(graphics, x, y, size, false);
        int textY = y + Math.max(0, (size - this.font.lineHeight) / 2);
        graphics.drawCenteredString(this.font, symbol, x + size / 2, textY, theme.controlText());
    }

    private boolean isDetailedNodeRendering() {
        return zoom >= 0.6D;
    }

    private void renderMachineSlot(GuiGraphics graphics, IDrawable icon, int x, int y) {
        RecipeTreeTheme.drawSlot(graphics, x, y);
        icon.draw(graphics, x + 1, y + 1);
    }

    private void renderNodeAmount(GuiGraphics graphics, String amount, int x, int y) {
        if (amount == null || amount.isBlank()) {
            return;
        }
        String label = amount.startsWith("x") ? amount.substring(1) : amount;
        label = label.replace(" ", "");
        int textWidth = this.font.width(label);
        int maxWidth = NODE_MAIN_SIZE - 6;
        float scale = Math.min(1.0F, maxWidth / (float) Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(x + NODE_MAIN_SIZE - 2, y + NODE_HEIGHT - 2, 300.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, label, -textWidth, -this.font.lineHeight, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private int substitutionToggleItemX() {
        return 10;
    }

    private int substitutionToggleY() {
        return this.height - 25;
    }

    private int substitutionToggleFluidX() {
        return substitutionToggleItemX() + SUBSTITUTION_ICON_DST + 6;
    }

    private void updateVisibleLogicalBounds(boolean clippedWorkspace) {
        double screenLeft = clippedWorkspace ? canvasLeft() + 2.0D : 0.0D;
        double screenTop = clippedWorkspace ? HEADER_HEIGHT + 2.0D : 0.0D;
        double screenRight = clippedWorkspace ? canvasRight() - 2.0D : this.width;
        double screenBottom = clippedWorkspace ? this.height - currentFooterHeight() - 2.0D : this.height;
        visibleLogicalMinX = (screenLeft - panX) / zoom - VISIBILITY_MARGIN;
        visibleLogicalMaxX = (screenRight - panX) / zoom + VISIBILITY_MARGIN;
        visibleLogicalMinY = (screenTop - panY) / zoom - VISIBILITY_MARGIN;
        visibleLogicalMaxY = (screenBottom - panY) / zoom + VISIBILITY_MARGIN;
    }

    private boolean isLogicalRectVisible(double left, double top, double right, double bottom) {
        return right >= visibleLogicalMinX
                && left <= visibleLogicalMaxX
                && bottom >= visibleLogicalMinY
                && top <= visibleLogicalMaxY;
    }

    private void renderPatternSubstitutionToggles(GuiGraphics graphics) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsSubstitution()) {
            return;
        }
        int iy = substitutionToggleY();
        int ix = substitutionToggleItemX();
        int fx = substitutionToggleFluidX();
        backend.renderSubstitutionIcon(graphics, ix, iy, SUBSTITUTION_ICON_SRC, SUBSTITUTION_ICON_DST, false);
        backend.renderSubstitutionIcon(graphics, fx, iy, SUBSTITUTION_ICON_SRC, SUBSTITUTION_ICON_DST, true);
    }

    private boolean handlePatternSubstitutionToggleClick(double mouseX, double mouseY) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsSubstitution()) {
            return false;
        }
        int ix = substitutionToggleItemX();
        int iy = substitutionToggleY();
        int fx = substitutionToggleFluidX();
        int s = SUBSTITUTION_ICON_DST;
        if (mouseX >= ix && mouseX < ix + s && mouseY >= iy && mouseY < iy + s) {
            backend.toggleItemSubstitute();
            return true;
        }
        if (mouseX >= fx && mouseX < fx + s && mouseY >= iy && mouseY < iy + s) {
            backend.toggleFluidSubstitute();
            return true;
        }
        return false;
    }

    private @Nullable List<Component> patternSubstitutionTooltipAt(int mouseX, int mouseY) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsSubstitution()) {
            return null;
        }
        int ix = substitutionToggleItemX();
        int iy = substitutionToggleY();
        int fx = substitutionToggleFluidX();
        int s = SUBSTITUTION_ICON_DST;
        if (mouseX >= ix && mouseX < ix + s && mouseY >= iy && mouseY < iy + s) {
            return backend.substitutionTooltip(false);
        }
        if (mouseX >= fx && mouseX < fx + s && mouseY >= iy && mouseY < iy + s) {
            return backend.substitutionTooltip(true);
        }
        return null;
    }

    private @Nullable List<Component> sidebarTooltipAt(double mouseX, double mouseY) {
        if (isPointInsideButton(autoMergeButton, mouseX, mouseY)) {
            return switchTooltip(autoMergeSameMaterials
                    ? "gui.jeict.recipe_tree.overview_merge_enabled"
                    : "gui.jeict.recipe_tree.overview_merge_disabled",
                    "gui.jeict.recipe_tree.overview_merge_tooltip");
        }
        if (isPointInsideButton(computeQuantitiesButton, mouseX, mouseY)) {
            return switchTooltip(computeRecipeQuantities
                    ? "gui.jeict.recipe_tree.overview_quantity_compute_enabled"
                    : "gui.jeict.recipe_tree.overview_quantity_compute_disabled",
                    "gui.jeict.recipe_tree.overview_quantity_compute_tooltip");
        }
        if (isPointInsideButton(autoUniqueRecipeButton, mouseX, mouseY)) {
            return switchTooltip(autoExpandUniqueEncodableRecipe
                    ? "gui.jeict.recipe_tree.overview_auto_unique_enabled"
                    : "gui.jeict.recipe_tree.overview_auto_unique_disabled",
                    "gui.jeict.recipe_tree.overview_auto_unique_tooltip");
        }
        if (isPointInsideButton(memoryReadingButton, mouseX, mouseY)) {
            return switchTooltip(readRememberedSelections
                    ? "gui.jeict.recipe_tree.overview_memory_reading_enabled"
                    : "gui.jeict.recipe_tree.overview_memory_reading_disabled",
                    "gui.jeict.recipe_tree.overview_memory_reading_tooltip");
        }
        if (isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
            return switchTooltip(context.disableExistingPatternExpansion()
                    ? "gui.jeict.recipe_tree.overview_toggle_existing_disabled"
                    : "gui.jeict.recipe_tree.overview_toggle_existing_enabled",
                    "gui.jeict.recipe_tree.overview_toggle_existing_tooltip");
        }
        if (isPointInsideButton(styleButton, mouseX, mouseY)) {
            return List.of(RecipeTreeTheme.styleButtonMessage(),
                    Component.translatable("gui.jeict.recipe_tree.overview_style_tooltip")
                            .withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    private List<Component> switchTooltip(String stateKey, String descriptionKey) {
        return List.of(Component.translatable(stateKey),
                Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }

    private boolean renderSummaryMaterialTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY,
            int mouseX, int mouseY) {
        for (TopMaterialBounds bounds : topMaterialBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY) || bounds.material().alternatives().isEmpty()) {
                continue;
            }
            ItemStack stack = bounds.material().alternatives().getFirst();
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            if (computeRecipeQuantities) {
                lines.add(Component.translatable("gui.jeict.recipe_tree.amount_exact", bounds.material().count())
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.jeict.recipe_tree.material_right_click_hint")
                    .withStyle(ChatFormatting.GRAY));
            if (cachedTopMaterialShowsPatternHint(bounds.material())) {
                lines.add(Component.translatable("gui.jeict.recipe_tree.me_pattern_exists_hint").withStyle(ChatFormatting.RED));
            }
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        for (GenericTopMaterialBounds bounds : genericTopMaterialBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY)) {
                continue;
            }
            MergedLeaf leaf = bounds.data().leaf();
            List<Component> lines = new ArrayList<>(ingredientTooltipLines(leaf.ingredient()));
            if (lines.isEmpty()) {
                lines.add(Component.literal(leaf.title()));
            }
            if (computeRecipeQuantities && !bounds.data().label().isBlank()) {
                lines.add(Component.literal(bounds.data().label()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.jeict.recipe_tree.material_right_click_hint")
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        for (TopMaterialBounds bounds : surplusMaterialBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY) || bounds.material().alternatives().isEmpty()) {
                continue;
            }
            ItemStack stack = bounds.material().alternatives().getFirst();
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            lines.add(Component.translatable("gui.jeict.recipe_tree.surplus_amount", bounds.material().count())
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void renderTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY, int mouseX, int mouseY) {
        if (pendingAlternativeSelection != null) {
            for (AlternativeOptionBounds option : alternativeOptionBounds) {
                if (option.contains(mouseX, mouseY) && option.ingredient() != null) {
                    renderVanillaIngredientTooltip(graphics, option.ingredient(), mouseX, mouseY);
                    return;
                }
            }
        }
        List<Component> substitutionTooltip = patternSubstitutionTooltipAt(mouseX, mouseY);
        if (substitutionTooltip != null) {
            graphics.renderTooltip(this.font, substitutionTooltip, Optional.empty(), mouseX, mouseY);
            return;
        }
        List<Component> sidebarTooltip = sidebarTooltipAt(mouseX, mouseY);
        if (sidebarTooltip != null) {
            graphics.renderTooltip(this.font, sidebarTooltip, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (isPointInsideBatchBadge(mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.batch_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoMergeSameMaterials) {
            if (encodeButton.visible && isPointInsideButton(encodeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_encode_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (uploadButton.visible && isPointInsideButton(uploadButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(uploadButton.active
                                ? Component.translatable("gui.jeict.recipe_tree.overview_upload_tooltip")
                                : Component.translatable("gui.jeict.recipe_tree.upload_missing_eaep")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (autoMergeButton.visible && isPointInsideButton(autoMergeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_merge_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (autoUniqueRecipeButton.visible && isPointInsideButton(autoUniqueRecipeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_auto_unique_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (memoryReadingButton.visible && isPointInsideButton(memoryReadingButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_memory_reading_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (computeQuantitiesButton.visible && isPointInsideButton(computeQuantitiesButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_quantity_compute_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (styleButton.visible && isPointInsideButton(styleButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_style_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (toggleExistingPatternButton.visible && isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable(context.disableExistingPatternExpansion()
                                ? "gui.jeict.recipe_tree.overview_toggle_existing_disabled"
                                : "gui.jeict.recipe_tree.overview_toggle_existing_enabled")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (!isInsideWorkspace(mouseX, mouseY)) {
                return;
            }
            for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                if (bounds.contains(logicalMouseX, logicalMouseY)) {
                    graphics.renderTooltip(this.font,
                            List.of(Component.translatable("gui.jeict.recipe_tree.alternative_button_hint")),
                            Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
            if (topMaterialsPinButtonBounds != null && topMaterialsPinButtonBounds.contains(logicalMouseX, logicalMouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.pin_materials_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            for (LayerMaterialBounds bounds : layerMaterialBounds) {
                if (bounds.contains(logicalMouseX, logicalMouseY)) {
                    List<Component> lines = new ArrayList<>(ingredientTooltipLines(bounds.material().ingredient()));
                    if (lines.isEmpty()) {
                        lines.add(Component.literal(bounds.material().label()));
                    }
                    if (!bounds.material().amountLabel().isBlank()) {
                        lines.add(Component.literal(bounds.material().amountLabel()).withStyle(ChatFormatting.GRAY));
                    }
                    if (bounds.material().showsPatternHint()) {
                        lines.add(Component.translatable("gui.jeict.recipe_tree.pattern_exists").withStyle(ChatFormatting.RED));
                    }
                    if (!bounds.material().recipeTargets().isEmpty()) {
                        RecipeTreeNodeViewModel node = bounds.material().recipeTargets().getFirst();
                        if (node.recipe().subtitle() != null && !node.recipe().subtitle().getString().isBlank()) {
                            lines.add(node.recipe().subtitle().copy().withStyle(ChatFormatting.GRAY));
                        }
                    }
                    graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
            if (renderSummaryMaterialTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY)) {
                return;
            }
            return;
        }
        if (encodeButton.visible && isPointInsideButton(encodeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_encode_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (uploadButton.visible && isPointInsideButton(uploadButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(uploadButton.active
                            ? Component.translatable("gui.jeict.recipe_tree.overview_upload_tooltip")
                            : Component.translatable("gui.jeict.recipe_tree.upload_missing_eaep")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoMergeButton.visible && isPointInsideButton(autoMergeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_merge_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoUniqueRecipeButton.visible && isPointInsideButton(autoUniqueRecipeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_auto_unique_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (computeQuantitiesButton.visible && isPointInsideButton(computeQuantitiesButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_quantity_compute_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (styleButton.visible && isPointInsideButton(styleButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_style_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (toggleExistingPatternButton.visible && isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable(context.disableExistingPatternExpansion()
                            ? "gui.jeict.recipe_tree.overview_toggle_existing_disabled"
                            : "gui.jeict.recipe_tree.overview_toggle_existing_enabled")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (!isInsideWorkspace(mouseX, mouseY)) {
            return;
        }
        for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.alternative_button_hint")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (topMaterialsPinButtonBounds != null && topMaterialsPinButtonBounds.contains(logicalMouseX, logicalMouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.pin_materials_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (renderSummaryMaterialTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY)) {
            return;
        }

        for (PositionedNode node : visiblePositionedNodes) {
            int x = node.x();
            int y = node.y();
            int width = node.graph().width();
            int collapseX = x + width - NODE_PART_PADDING - 10;
            int collapseY = y + 9;
            if (showsCollapseButton(node)
                    && logicalMouseX >= collapseX && logicalMouseX <= collapseX + 10
                    && logicalMouseY >= collapseY && logicalMouseY <= collapseY + 10) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.collapse_branch")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (logicalMouseX < x || logicalMouseX > x + width || logicalMouseY < y || logicalMouseY > y + NODE_HEIGHT) {
                continue;
            }
            if (node.graph().showsPatternHint()) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.pattern_exists").withStyle(ChatFormatting.RED)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            int machineX = x + NODE_MAIN_SIZE + NODE_PART_GAP;
            if (node.graph().machineIcon() != null
                    && logicalMouseX >= machineX && logicalMouseX <= machineX + MACHINE_SLOT_SIZE
                    && logicalMouseY >= y + 5 && logicalMouseY <= y + 5 + MACHINE_SLOT_SIZE) {
                List<Component> lines = new ArrayList<>();
                lines.add(node.graph().titleComponent());
                if (node.graph().machineName() != null && !node.graph().machineName().getString().isBlank()) {
                    lines.add(node.graph().machineName().copy().withStyle(ChatFormatting.GRAY));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
            if (logicalMouseX >= x + 2 && logicalMouseX <= x + 22) {
                List<Component> lines = new ArrayList<>(ingredientTooltipLines(node.graph().ingredient()));
                if (lines.isEmpty()) {
                    lines.add(node.graph().titleComponent());
                }
                if (!node.graph().exactAmount().isBlank()) {
                    lines.add(Component.literal(node.graph().exactAmount()).withStyle(ChatFormatting.GRAY));
                }
                if (node.graph().recipeNode() != null && node.graph().machineName() != null
                        && !node.graph().machineName().getString().isBlank()) {
                    lines.add(node.graph().machineName().copy().withStyle(ChatFormatting.GRAY));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointInsideBatchBadge(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handlePatternSubstitutionToggleClick(mouseX, mouseY)) {
            return true;
        }
        boolean insideWorkspace = isInsideWorkspace(mouseX, mouseY);
        double logicalMouseXForPin = (mouseX - panX) / zoom;
        double logicalMouseYForPin = (mouseY - panY) / zoom;
        if (insideWorkspace && button == 0 && topMaterialsPinButtonBounds != null
                && topMaterialsPinButtonBounds.contains(logicalMouseXForPin, logicalMouseYForPin)) {
            FloatingMaterialOverlayState.set(createFloatingMaterialSnapshot());
            this.onClose();
            return true;
        }
        if (insideWorkspace && button == 0) {
            for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                if (bounds.contains(logicalMouseXForPin, logicalMouseYForPin)) {
                    openAlternativeSelection(bounds);
                    return true;
                }
            }
        }
        if (insideWorkspace && (button == 0 || button == 1)) {
            GenericTopMaterialBounds genericTarget = findGenericTopMaterialAt(logicalMouseXForPin, logicalMouseYForPin);
            if (genericTarget != null) {
                if (button == 0) {
                    openSelectionWithJei(genericTarget.data().leaf());
                } else {
                    jumpToMaterialSignature(signatureOf(genericTarget.data().leaf().representative()));
                }
                return true;
            }
        }
        if (autoMergeSameMaterials) {
            if (pendingAlternativeSelection != null) {
                for (AlternativeOptionBounds option : alternativeOptionBounds) {
                    if (option.contains(mouseX, mouseY)) {
                        selectAlternative(option.index());
                        return true;
                    }
                }
                pendingAlternativeSelection = null;
            }
            if (!insideWorkspace) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            if (button == 0) {
                for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                    if (bounds.contains(logicalMouseX, logicalMouseY)) {
                        openAlternativeSelection(bounds);
                        return true;
                    }
                }
            }
            if (button == 1) {
                TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
                if (materialTarget != null) {
                    jumpToMaterial(materialTarget.material());
                    return true;
                }
            }
            if (button == 0) {
                TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
                if (materialTarget != null) {
                    MergedLeaf leaf = findLeafForMaterial(materialTarget.material());
                    if (leaf != null) {
                        openSelectionWithJei(leaf);
                    }
                    return true;
                }
            }
            LayerMaterialBounds clicked = findLayerAt(logicalMouseX, logicalMouseY);
            if (clicked != null) {
                if (button == 0) {
                    selectedLayerMaterial = clicked.material();
                    selectedNode = null;
                    ensureLogicalRectVisible(clicked.x(), clicked.y(), clicked.width(), clicked.height());
                    return true;
                }
                if (button == 1 && handleMergedLayerMaterialClick(clicked.material())) {
                    return true;
                }
            }
            if (button == 0) {
                selectedLayerMaterial = null;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingAlternativeSelection != null) {
            for (AlternativeOptionBounds option : alternativeOptionBounds) {
                if (option.contains(mouseX, mouseY)) {
                    selectAlternative(option.index());
                    return true;
                }
            }
            pendingAlternativeSelection = null;
        }
        if (!insideWorkspace) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
            if (materialTarget != null) {
                jumpToMaterial(materialTarget.material());
                return true;
            }
        }
        if (button == 0) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
            if (materialTarget != null) {
                MergedLeaf leaf = findLeafForMaterial(materialTarget.material());
                if (leaf != null) {
                    openSelectionWithJei(leaf);
                }
                return true;
            }
        }
        if (button == 0 || button == 1) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            if (button == 0) {
                for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                    if (bounds.contains(logicalMouseX, logicalMouseY)) {
                        openAlternativeSelection(bounds);
                        return true;
                    }
                }
            }
            if (button == 0) {
                PositionedNode collapseTarget = findCollapseButtonAt(logicalMouseX, logicalMouseY);
                if (collapseTarget != null) {
                    collapseNode(collapseTarget);
                    closeSelection();
                    rebuildLayout();
                    return true;
                }
            }
            PositionedNode clicked = findNodeAt(logicalMouseX, logicalMouseY);
            if (clicked != null) {
                if (button == 0) {
                    selectedNode = clicked;
                    selectedLayerMaterial = null;
                    ensureLogicalRectVisible(clicked.x(), clicked.y(), clicked.graph().width(), NODE_HEIGHT);
                    return true;
                }
                if (clicked.graph().recipeNode() != null) {
                    openSelectionWithJei(clicked.graph().recipeNode());
                    return true;
                }
                if (clicked.graph().mergedLeaf() != null) {
                    if (clicked.graph().showsPatternHint()) {
                        return true;
                    }
                    openSelectionWithJei(clicked.graph().mergedLeaf());
                    return true;
                }
            }
            if (button == 0) {
                selectedNode = null;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private @Nullable PositionedNode findNodeAt(double logicalMouseX, double logicalMouseY) {
        for (PositionedNode node : visiblePositionedNodes) {
            if (logicalMouseX >= node.x() && logicalMouseX <= node.x() + node.graph().width()
                    && logicalMouseY >= node.y() && logicalMouseY <= node.y() + NODE_HEIGHT) {
                return node;
            }
        }
        return null;
    }

    private @Nullable GenericTopMaterialBounds findGenericTopMaterialAt(double logicalMouseX, double logicalMouseY) {
        for (GenericTopMaterialBounds bounds : genericTopMaterialBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                return bounds;
            }
        }
        return null;
    }

    private @Nullable TopMaterialBounds findTopMaterialAt(double logicalMouseX, double logicalMouseY) {
        for (TopMaterialBounds bounds : topMaterialBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                return bounds;
            }
        }
        return null;
    }

    private @Nullable MergedLeaf findLeafForMaterial(RequestedIngredient material) {
        List<UnresolvedInputSlot> matches = collectUnresolvedInputsForMaterial(material);
        if (matches.isEmpty()) {
            return null;
        }
        RecipeTreeInputViewModel representative = matches.getFirst().input();
        List<RecipeTreeInputViewModel> members = new ArrayList<>(matches.size());
        List<RecipeTreeNodeViewModel> parents = new ArrayList<>(matches.size());
        int totalAmount = 0;
        for (UnresolvedInputSlot match : matches) {
            members.add(match.input());
            parents.add(match.parentNode());
            totalAmount = safeAdd(totalAmount, Math.max(1, match.input().amount()));
        }
        return new MergedLeaf(resolveGroupIngredient(members), displayNameOf(representative), totalAmount,
                representative.amountText(), matches.getFirst().parentNode(), List.copyOf(members), List.copyOf(parents));
    }

    private List<UnresolvedInputSlot> collectUnresolvedInputsForMaterial(RequestedIngredient material) {
        List<UnresolvedInputSlot> indexed = unresolvedInputsBySignature.get(signatureOf(material));
        return indexed == null ? List.of() : indexed;
    }

    private @Nullable PositionedNode findCollapseButtonAt(double logicalMouseX, double logicalMouseY) {
        for (PositionedNode node : visiblePositionedNodes) {
            if (!showsCollapseButton(node)) {
                continue;
            }
            int x = node.x() + node.graph().width() - NODE_PART_PADDING - 10;
            int y = node.y() + 9;
            if (logicalMouseX >= x && logicalMouseX <= x + 10
                    && logicalMouseY >= y && logicalMouseY <= y + 10) {
                return node;
            }
        }
        return null;
    }

    private boolean showsCollapseButton(PositionedNode node) {
        return !node.graph().children().isEmpty()
                && node.graph().recipeNode() != null
                && node.graph().recipeNode().parent() != null;
    }

    private boolean shouldBlockExpansion(MergedLeaf leaf) {
        return context.disableExistingPatternExpansion() && hasExistingPatternForLeaf(leaf);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isPointInsideBatchBadge(mouseX, mouseY)) {
            int direction = (int) Math.signum(scrollY);
            if (direction != 0) {
                adjustBatchCount(direction * (Screen.hasShiftDown() ? 10 : 1));
            }
            return true;
        }
        if (pendingAlternativeSelection != null && isInsideAlternativeViewport(mouseX, mouseY)
                && getAlternativeOptionCount() > getAlternativeVisibleCount()) {
            alternativeScroll -= (int) Math.signum(scrollY);
            clampAlternativeScroll();
            return true;
        }
        if (!isInsideWorkspace(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (Screen.hasShiftDown()) {
            panX += scrollY * 20.0D;
            return true;
        }
        zoomAt(mouseX, mouseY, scrollY * 0.1D);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && isInsideWorkspace(mouseX, mouseY)) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        pendingJeiSelection = null;
        pendingAlternativeSelection = null;
        RecipeTreeClientMemory.flushPendingSave();
        this.minecraft.setScreen(returnScreen);
    }

    private void rememberSelectionForNode(RecipeTreeNodeViewModel targetNode, RecipeTreeRecipeViewModel selected) {
        RecipeTreeNodeViewModel parent = targetNode.parent();
        if (parent == null) {
            return;
        }
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() != targetNode) {
                continue;
            }
            String signature = signatureOf(input);
            forgetManualCollapse(signature);
            context.rememberSelection(signature, selected);
        }
    }

    private boolean containsRecipeInAncestors(@Nullable RecipeTreeNodeViewModel node, RecipeTreeRecipeViewModel candidate) {
        for (RecipeTreeNodeViewModel cursor = node; cursor != null; cursor = cursor.parent()) {
            if (cursor.recipe().sameRecipeAs(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void applyRecipeBatchSelection(List<RecipeTreeNodeViewModel> batch, RecipeTreeRecipeViewModel selected) {
        for (RecipeTreeNodeViewModel targetNode : batch) {
            if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_recursive_recipe")
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
        }
        Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache = new HashMap<>();
        for (RecipeTreeNodeViewModel targetNode : batch) {
            rememberSelectionForNode(targetNode, selected);
            targetNode.setRecipe(selected);
            autoApplyRememberedChildren(targetNode, rememberedCache);
        }
        pendingJeiSelection = null;
    }

    private void applySelectedRecipe(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            RecipeTreeRecipeViewModel selected) {
        if (targetNode != null) {
            if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_recursive_recipe")
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
            rememberSelectionForNode(targetNode, selected);
            targetNode.setRecipe(selected);
            autoApplyRememberedChildren(targetNode);
        } else if (targetLeaf != null) {
            for (int i = 0; i < targetLeaf.members().size(); i++) {
                if (targetLeaf.parentForMember(i).containsRecipe(selected)) {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_recursive_recipe")
                                .withStyle(ChatFormatting.RED), true);
                    }
                    return;
                }
            }
            applyLeafSelection(targetLeaf, selected);
        }
        pendingJeiSelection = null;
        rebuildLayout();
    }

    private boolean openSelectionWithJei(RecipeTreeNodeViewModel targetNode) {
        ITypedIngredient<?> ingredient = targetNode.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(targetNode, null, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openSelectionWithJei(MergedLeaf targetLeaf) {
        ITypedIngredient<?> ingredient = getJeiSelectionIngredient(targetLeaf.representative());
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(null, targetLeaf, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openRecipeTargetsSelectionWithJei(List<RecipeTreeNodeViewModel> targets) {
        if (targets.isEmpty()) {
            return false;
        }
        RecipeTreeNodeViewModel first = targets.getFirst();
        ITypedIngredient<?> ingredient = first.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(first, null, List.copyOf(targets));
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean handleMergedLayerMaterialClick(LayerMaterial material) {
        if (!material.recipeTargets().isEmpty()) {
            return openRecipeTargetsSelectionWithJei(material.recipeTargets());
        }
        if (!material.leafInputs().isEmpty()) {
            List<RecipeTreeNodeViewModel> expandedTargets = new ArrayList<>();
            for (RecipeTreeInputViewModel input : material.leafInputs()) {
                RecipeTreeNodeViewModel child = input.child();
                if (child != null && !expandedTargets.contains(child)) {
                    expandedTargets.add(child);
                }
            }
            if (!expandedTargets.isEmpty()) {
                return openRecipeTargetsSelectionWithJei(expandedTargets);
            }
            if (!material.hasUnresolvedLeaves()) {
                return false;
            }
            MergedLeaf leaf = mergedLeafFromLayerMaterial(material);
            if (leaf == null) {
                return false;
            }
            if (shouldBlockExpansion(leaf)) {
                return true;
            }
            return openSelectionWithJei(leaf);
        }
        return false;
    }

    private @Nullable ITypedIngredient<?> getJeiSelectionIngredient(RecipeTreeInputViewModel input) {
        ITypedIngredient<?> ingredient = input.displayIngredient();
        if (ingredient != null) {
            return ingredient;
        }
        ItemStack stack = input.displayStack();
        if (stack.isEmpty()) {
            return null;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return null;
        }
        return ingredientManager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
    }

    @Override
    public void jeict$applyJeiRecipe(Object recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlots) {
        if (pendingJeiSelection == null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_select_target_first")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        RecipeTreeRecipeViewModel selected = RecipeTreeJeiLookup.createRootSnapshot(recipe, recipeSlots);
        List<RecipeTreeNodeViewModel> batch = pendingJeiSelection.recipeApplyBatch();
        if (!batch.isEmpty()) {
            applyRecipeBatchSelection(batch, selected);
            pendingJeiSelection = null;
            rebuildLayout();
            return;
        }
        applySelectedRecipe(pendingJeiSelection.targetNode(), pendingJeiSelection.targetLeaf(), selected);
    }

    private void applyLeafSelection(MergedLeaf target, RecipeTreeRecipeViewModel selected) {
        applyLeafSelection(target, selected, new HashMap<>());
    }

    private void applyLeafSelection(MergedLeaf target, RecipeTreeRecipeViewModel selected,
            Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache) {
        Set<String> rememberedSignatures = new HashSet<>();
        for (RecipeTreeInputViewModel input : target.members()) {
            String signature = signatureOf(input);
            if (rememberedSignatures.add(signature)) {
                forgetManualCollapse(signature);
                context.rememberSelection(signature, selected);
            }
        }
        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> byParent = new LinkedHashMap<>();
        for (int i = 0; i < target.members().size(); i++) {
            RecipeTreeInputViewModel input = target.members().get(i);
            RecipeTreeNodeViewModel owner = target.parentForMember(i);
            byParent.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(input);
        }
        for (Map.Entry<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> entry : byParent.entrySet()) {
            RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, entry.getKey());
            for (RecipeTreeInputViewModel input : entry.getValue()) {
                input.setChild(childNode);
            }
            autoApplyRememberedChildren(childNode, rememberedCache);
        }
    }

    private void autoApplyRememberedChildren(RecipeTreeNodeViewModel parent) {
        autoApplyRememberedChildren(parent, new HashMap<>());
    }

    private void autoApplyRememberedChildren(RecipeTreeNodeViewModel parent,
            Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache) {
        if (!readRememberedSelections) {
            return;
        }
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        autoApplyRememberedChildren(parent, visited, rememberedCache);
    }

    private void autoApplyRememberedChildren(RecipeTreeNodeViewModel parent,
            Set<RecipeTreeNodeViewModel> visited,
            Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache) {
        if (!visited.add(parent)) {
            return;
        }
        List<RecipeTreeNodeViewModel> childrenToVisit = new ArrayList<>();
        Set<RecipeTreeNodeViewModel> queuedChildren = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, RecipeTreeNodeViewModel> rememberedChildrenBySignature = new HashMap<>();
        Map<String, RecipeTreeNodeViewModel> expandedChildrenBySignature = new HashMap<>();
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            String signature = signatureOf(input);
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                RecipeTreeNodeViewModel canonical = expandedChildrenBySignature.get(signature);
                if (canonical != null && canonical.recipe().sameRecipeAs(child.recipe())) {
                    input.setChild(canonical);
                    child = canonical;
                } else {
                    expandedChildrenBySignature.putIfAbsent(signature, child);
                }
            } else if (!isManuallyCollapsed(signature)) {
                RecipeTreeRecipeViewModel remembered = rememberedCache
                        .computeIfAbsent(signature,
                                key -> Optional.ofNullable(context.getRememberedSelection(key, input.displayIngredient())))
                        .orElse(null);
                if (remembered != null && !parent.containsRecipe(remembered)) {
                    RecipeTreeNodeViewModel existing = expandedChildrenBySignature.get(signature);
                    if (existing != null && existing.recipe().sameRecipeAs(remembered)) {
                        child = existing;
                    } else {
                        child = rememberedChildrenBySignature.computeIfAbsent(signature,
                                ignored -> new RecipeTreeNodeViewModel(remembered, parent));
                        expandedChildrenBySignature.putIfAbsent(signature, child);
                    }
                    input.setChild(child);
                }
            }
            if (child != null && queuedChildren.add(child)) {
                childrenToVisit.add(child);
            }
        }
        for (RecipeTreeNodeViewModel child : childrenToVisit) {
            autoApplyRememberedChildren(child, visited, rememberedCache);
        }
    }

    private boolean hasManuallyCollapsedInput(MergedLeaf leaf) {
        for (RecipeTreeInputViewModel input : leaf.members()) {
            if (isManuallyCollapsed(signatureOf(input))) {
                return true;
            }
        }
        return false;
    }

    private boolean isManuallyCollapsed(String signature) {
        return manuallyCollapsedSignatures.contains(signature) || context.isCollapsed(signature);
    }

    private void rememberManualCollapse(String signature) {
        manuallyCollapsedSignatures.add(signature);
        context.rememberCollapsed(signature);
    }

    private void forgetManualCollapse(String signature) {
        manuallyCollapsedSignatures.remove(signature);
        context.forgetCollapsed(signature);
    }

    private String signatureOf(RecipeTreeInputViewModel input) {
        int alternativeIndex = input.selectedAlternativeIndex();
        CachedInputSignature cached = inputSignatureCache.get(input);
        if (cached != null && cached.alternativeIndex() == alternativeIndex) {
            return cached.signature();
        }
        String signature;
        RequestedIngredient requested = input.requestedIngredientView();
        if (requested != null && !requested.alternatives().isEmpty()) {
            signature = input.requestedIngredientSignature();
            if (signature == null || signature.isBlank()) {
                signature = signatureOf(requested);
            }
        } else {
            ItemStack stack = input.displayStack();
            ITypedIngredient<?> ingredient = input.displayIngredient();
            if (!stack.isEmpty()) {
                signature = signatureOfItemType(stack);
            } else if (ingredient != null) {
                signature = signatureOfMaterialIngredient(ingredient);
            } else {
                signature = "name#" + displayNameOf(input);
            }
        }
        inputSignatureCache.put(input, new CachedInputSignature(alternativeIndex, signature));
        return signature;
    }

    private String signatureOf(ITypedIngredient<?> ingredient) {
        if (ingredient == null) {
            return "null";
        }
        String cached = typedIngredientSignatureCache.get(ingredient);
        if (cached != null) {
            return cached;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        String signature;
        if (ingredientManager != null) {
            signature = signatureOfTypedIngredient(ingredientManager, ingredient);
        } else {
            Object raw = ingredient.getIngredient();
            signature = "typed#" + raw.getClass().getName() + "#" + raw;
        }
        typedIngredientSignatureCache.put(ingredient, signature);
        return signature;
    }

    private String signatureOf(RecipeTreeNodeViewModel node) {
        ITypedIngredient<?> primaryOutput = node.recipe().primaryOutputIngredient();
        if (primaryOutput != null) {
            return signatureOf(primaryOutput);
        }
        return "node#" + node.recipe().title().getString();
    }

    private String displayNameOfOutput(RecipeTreeNodeViewModel node) {
        return node.recipe().title().getString();
    }

    private String signatureOfNode(RecipeTreeNodeViewModel node) {
        return signatureOf(node);
    }

    private static String signatureOfTypedIngredient(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return signatureOfTypedIngredientTyped(ingredientManager, ingredient);
    }

    private static <T> String signatureOfTypedIngredientTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return typed.getType().getUid() + "#" + ingredientManager.getIngredientHelper(typed.getType()).getUid(typed,
                mezz.jei.api.ingredients.subtypes.UidContext.Ingredient);
    }

    private void closeSelection() {
        this.pendingJeiSelection = null;
        this.pendingAlternativeSelection = null;
        this.alternativeScroll = 0;
        this.alternativeOptionBounds.clear();
        updateSelectionButtons();
    }

    private void collapseNode(PositionedNode positionedNode) {
        RecipeTreeNodeViewModel node = positionedNode.graph().recipeNode();
        RecipeTreeNodeViewModel parent = node == null ? null : node.parent();
        if (parent == null) {
            return;
        }
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() != node) {
                continue;
            }
            String signature = signatureOf(input);
            rememberManualCollapse(signature);
            collapseExpandedInputsBySignature(signature);
            return;
        }
    }

    private void collapseExpandedInputsBySignature(String targetSignature) {
        if (targetSignature == null || targetSignature.isBlank()) {
            return;
        }
        ArrayDeque<RecipeTreeNodeViewModel> pending = new ArrayDeque<>();
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        pending.addLast(context.root());
        while (!pending.isEmpty()) {
            RecipeTreeNodeViewModel node = pending.removeLast();
            if (!visited.add(node)) {
                continue;
            }
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                RecipeTreeNodeViewModel child = input.child();
                if (child == null) {
                    continue;
                }
                if (targetSignature.equals(signatureOf(input))) {
                    input.setChild(null);
                } else {
                    pending.addLast(child);
                }
            }
        }
    }

    private void jumpToMaterial(RequestedIngredient material) {
        jumpToMaterialSignature(signatureOf(material));
    }

    private void jumpToMaterialSignature(String targetSignature) {
        if (autoMergeSameMaterials) {
            jumpToMaterialMergedLayers(targetSignature);
            return;
        }
        PositionedNode matchedLeaf = null;
        double logicalCenterX = (this.width * 0.5D - panX) / zoom;
        double logicalCenterY = (this.height * 0.5D - panY) / zoom;
        double bestDistance = Double.MAX_VALUE;
        for (PositionedNode node : positionedNodes) {
            MergedLeaf leaf = node.graph().mergedLeaf();
            if (leaf == null || !signatureOf(leaf.representative()).equals(targetSignature)) {
                continue;
            }
            double centerX = node.x() + node.graph().width() / 2.0D;
            double centerY = node.y() + NODE_HEIGHT / 2.0D;
            double distance = Math.pow(centerX - logicalCenterX, 2) + Math.pow(centerY - logicalCenterY, 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                matchedLeaf = node;
            }
        }
        if (matchedLeaf == null) {
            return;
        }
        panX = this.width * 0.5D - (matchedLeaf.x() + matchedLeaf.graph().width() / 2.0D) * zoom;
        panY = this.height * 0.45D - (matchedLeaf.y() + NODE_HEIGHT / 2.0D) * zoom;
    }

    private boolean layerMaterialMatchesSignature(LayerMaterial mat, String targetSignature) {
        for (RecipeTreeInputViewModel input : mat.leafInputs()) {
            if (signatureOf(input).equals(targetSignature)) {
                return true;
            }
        }
        for (RecipeTreeNodeViewModel node : mat.recipeTargets()) {
            if (signatureOf(node).equals(targetSignature)) {
                return true;
            }
        }
        return false;
    }

    private void jumpToMaterialMergedLayers(String targetSignature) {
        int bestDepth = Integer.MAX_VALUE;
        for (int d = 0; d < mergedLayerRows.size(); d++) {
            for (LayerMaterial mat : mergedLayerRows.get(d).materials()) {
                if (layerMaterialMatchesSignature(mat, targetSignature)) {
                    bestDepth = Math.min(bestDepth, d);
                    break;
                }
            }
        }
        if (bestDepth == Integer.MAX_VALUE) {
            return;
        }
        double targetLogicalY = 42 + TOP_MATERIALS_OFFSET + bestDepth * LEVEL_GAP + NODE_HEIGHT / 2.0D;
        panY = this.height * 0.45D - targetLogicalY * zoom;
    }

    private boolean hasExistingPatternForLeaf(MergedLeaf leaf) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsExistingPatternHints()) {
            return false;
        }
        RecipeTreeInputViewModel representative = leaf.representative();
        String signature = signatureOf(representative);
        return existingPatternMaterialCache.computeIfAbsent(signature, ignored -> {
            ITypedIngredient<?> displayed = representative.displayIngredient();
            if (displayed != null) {
                return backend.isCraftable(displayed.getIngredient());
            }
            return queryExistingPatternForRequestedIngredient(backend, representative.requestedIngredient());
        });
    }

    /** 仅检查首个非空备选，避免 tag 膨胀时造成查询风暴（与 JEI「当前展示」语义一致）。 */
    private boolean hasExistingPatternForRequestedIngredient(@Nullable RequestedIngredient ingredient) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsExistingPatternHints() || ingredient == null) {
            return false;
        }
        String signature = signatureOf(ingredient);
        return existingPatternMaterialCache.computeIfAbsent(signature,
                ignored -> queryExistingPatternForRequestedIngredient(backend, ingredient));
    }

    private boolean queryExistingPatternForRequestedIngredient(CraftingTreeBackend backend,
            @Nullable RequestedIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (ItemStack alternative : ingredient.alternatives()) {
            if (!alternative.isEmpty()) {
                return backend.isCraftable(alternative);
            }
        }
        return false;
    }

    private String signatureOf(RequestedIngredient ingredient) {
        List<String> parts = new ArrayList<>();
        for (ItemStack alternative : ingredient.alternatives()) {
            if (!alternative.isEmpty()) {
                parts.add(signatureOfItemType(alternative));
            }
        }
        parts.sort(String::compareTo);
        return "requested#" + String.join("|", parts);
    }

    private String signatureOfMaterialIngredient(ITypedIngredient<?> ingredient) {
        ItemStack itemStack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            return signatureOfItemType(itemStack);
        }
        Object raw = ingredient.getIngredient();
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager != null) {
            return signatureOfTypedIngredient(ingredientManager, ingredient);
        }
        return "typed#" + raw.getClass().getName() + "#" + raw;
    }

    private String signatureOfItemType(ItemStack stack) {
        return "itemtype#" + stack.getItem();
    }

    private void updateSelectionButtons() {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        backButton.visible = true;
        backButton.active = true;
        zoomOutButton.visible = true;
        zoomOutButton.active = zoom > 0.2D;
        zoomInButton.visible = true;
        zoomInButton.active = zoom < 4.0D;
        fitViewButton.visible = true;
        fitViewButton.active = true;
        settingsButton.visible = true;
        settingsButton.active = true;

        computeQuantitiesButton.visible = true;
        computeQuantitiesButton.active = true;
        autoUniqueRecipeButton.visible = true;
        autoUniqueRecipeButton.active = true;
        memoryReadingButton.visible = true;
        memoryReadingButton.active = true;
        autoMergeButton.visible = true;
        autoMergeButton.active = true;
        styleButton.visible = true;
        styleButton.active = true;
        toggleExistingPatternButton.visible = backend != null && backend.supportsExistingPatternHints();
        toggleExistingPatternButton.active = toggleExistingPatternButton.visible;

        int settingsX = 8;
        int settingsWidth = settingsOpen ? SETTINGS_WIDTH - 14 : 24;
        Button[] settingsButtons = {
                autoMergeButton, computeQuantitiesButton, autoUniqueRecipeButton, memoryReadingButton,
                toggleExistingPatternButton, styleButton
        };
        settingsButton.setX(settingsX);
        settingsButton.setY(HEADER_HEIGHT + 8);
        settingsButton.setWidth(24);
        settingsButton.setHeight(24);
        settingsButton.setMessage(Component.empty());
        int settingsY = HEADER_HEIGHT + 36;
        for (Button button : settingsButtons) {
            if (!button.visible) {
                continue;
            }
            button.setX(settingsX);
            button.setY(settingsY);
            button.setWidth(settingsWidth);
            button.setHeight(24);
            settingsY += 28;
        }

        fitViewButton.setX(this.width - 54);
        fitViewButton.setY(8);
        fitViewButton.setWidth(42);
        fitViewButton.setHeight(20);
        zoomInButton.setX(this.width - 80);
        zoomInButton.setY(8);
        zoomInButton.setWidth(22);
        zoomInButton.setHeight(20);
        zoomOutButton.setX(this.width - 154);
        zoomOutButton.setY(8);
        zoomOutButton.setWidth(22);
        zoomOutButton.setHeight(20);
        toolbarLeft = this.width - 160;

        encodeButton.visible = backend != null && backend.supportsEncode();
        encodeButton.active = encodeButton.visible;
        uploadButton.visible = backend != null;
        uploadButton.active = backend != null && backend.supportsUpload();
        encodeButton.setWidth(Math.max(72, this.font.width(encodeButton.getMessage()) + 14));
        uploadButton.setWidth(Math.max(54, this.font.width(uploadButton.getMessage()) + 14));
        encodeButton.setHeight(20);
        uploadButton.setHeight(20);
        int actionsRight = this.width - 10;
        if (uploadButton.visible) {
            uploadButton.setX(actionsRight - uploadButton.getWidth());
            uploadButton.setY(this.height - 27);
            actionsRight = uploadButton.getX() - 5;
        }
        if (encodeButton.visible) {
            encodeButton.setX(actionsRight - encodeButton.getWidth());
            encodeButton.setY(this.height - 27);
        }
        syncComputeQuantitiesButton();
        syncStyleButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncMemoryReadingButton();
        syncAutoMergeButton();
    }

    private void renderAlternativeSelection(GuiGraphics graphics, int mouseX, int mouseY) {
        alternativeOptionBounds.clear();
        if (pendingAlternativeSelection == null) {
            return;
        }

        List<DisplayOption> alternatives = pendingAlternativeSelection.alternatives();
        if (alternatives.isEmpty()) {
            pendingAlternativeSelection = null;
            return;
        }

        int visibleCount = getAlternativeVisibleCount();
        int panelWidth = 148;
        int panelHeight = visibleCount * 18 + 6;
        int panelX = Math.max(6, Math.min(this.width - panelWidth - 6, pendingAlternativeSelection.anchorX() + 12));
        int panelY = Math.max(6, Math.min(this.height - panelHeight - 6, pendingAlternativeSelection.anchorY() - 4));
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawFramedPanel(graphics, panelX, panelY, panelX + panelWidth, panelY + panelHeight);

        clampAlternativeScroll();
        int start = alternativeScroll;
        int end = Math.min(alternatives.size(), start + visibleCount);
        for (int i = start; i < end; i++) {
            int optionY = panelY + 4 + (i - start) * 18;
            DisplayOption option = alternatives.get(i);
            boolean selected = i == pendingAlternativeSelection.selectedAlternativeIndex();
            if (selected) {
                graphics.fill(panelX + 3, optionY, panelX + panelWidth - 3, optionY + 17, theme.selectedFill());
            }
            renderTypedSlot(graphics, panelX + 4, optionY, option.typedIngredient());
            graphics.drawString(this.font, trimToWidth(Component.literal(option.label()), panelWidth - 28),
                    panelX + 24, optionY + 5, theme.alternativeText(), false);
            alternativeOptionBounds.add(new AlternativeOptionBounds(i, option.typedIngredient(), panelX + 3, optionY, panelWidth - 6, 17));
        }
    }

    private void openAlternativeSelection(AlternativeButtonBounds bounds) {
        List<RecipeTreeInputViewModel> members;
        if (bounds.leaf() != null) {
            members = bounds.leaf().members();
        } else if (bounds.material() != null) {
            members = collectUnresolvedInputsForMaterial(bounds.material()).stream()
                    .map(UnresolvedInputSlot::input)
                    .toList();
        } else {
            members = List.of();
        }
        if (members.isEmpty()) {
            pendingAlternativeSelection = null;
            return;
        }
        RecipeTreeInputViewModel representative = members.get(0);
        int anchorX = (int) Math.round(bounds.x() * zoom + panX);
        int anchorY = (int) Math.round(bounds.y() * zoom + panY);
        pendingAlternativeSelection = new PendingAlternativeSelection(List.copyOf(members), representative.displayOptions(),
                representative.selectedAlternativeIndex(), anchorX, anchorY);
        alternativeScroll = 0;
    }

    private void selectAlternative(int index) {
        if (pendingAlternativeSelection == null) {
            return;
        }
        for (RecipeTreeInputViewModel member : pendingAlternativeSelection.members()) {
            member.selectAlternative(index);
        }
        pendingAlternativeSelection = null;
        rebuildLayout();
    }

    private int getAlternativeVisibleCount() {
        return 8;
    }

    private int getAlternativeOptionCount() {
        return pendingAlternativeSelection == null ? 0 : pendingAlternativeSelection.alternatives().size();
    }

    private void clampAlternativeScroll() {
        int maxScroll = Math.max(0, getAlternativeOptionCount() - getAlternativeVisibleCount());
        alternativeScroll = Math.max(0, Math.min(maxScroll, alternativeScroll));
    }

    private boolean isInsideAlternativeViewport(double mouseX, double mouseY) {
        if (pendingAlternativeSelection == null) {
            return false;
        }
        int panelWidth = 148;
        int panelHeight = getAlternativeVisibleCount() * 18 + 6;
        int panelX = Math.max(6, Math.min(this.width - panelWidth - 6, pendingAlternativeSelection.anchorX() + 12));
        int panelY = Math.max(6, Math.min(this.height - panelHeight - 6, pendingAlternativeSelection.anchorY() - 4));
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    private Component trimToWidth(Component text, int maxWidth) {
        return Component.literal(this.font.substrByWidth(text, Math.max(8, maxWidth)).getString());
    }

    private void renderTypedSlot(GuiGraphics graphics, int x, int y, @Nullable ITypedIngredient<?> ingredient) {
        ItemStack itemStack = extractItemStack(ingredient);
        renderSlot(graphics, x, y, itemStack);
        if (itemStack.isEmpty() && ingredient != null) {
            renderIngredientAt(graphics, ingredient, x + 1, y + 1);
        }
    }

    private void renderSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        RecipeTreeTheme.drawSlot(graphics, x, y);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }
    }


    private void renderIngredientAt(GuiGraphics graphics, @Nullable ITypedIngredient<?> ingredient, int x, int y) {
        if (ingredient == null) {
            return;
        }
        ItemStack itemStack = extractItemStack(ingredient);
        if (!itemStack.isEmpty()) {
            graphics.renderItem(itemStack, x, y);
            return;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        renderJeiIngredientTyped(graphics, ingredientManager, ingredient, x, y);
        graphics.pose().popPose();
    }

    private void renderVanillaIngredientTooltip(GuiGraphics graphics, ITypedIngredient<?> ingredient, int mouseX, int mouseY) {
        List<Component> tooltip = ingredientTooltipLines(ingredient);
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> ingredientTooltipLines(@Nullable ITypedIngredient<?> ingredient) {
        if (ingredient == null) {
            return List.of();
        }
        ItemStack stack = extractItemStack(ingredient);
        if (!stack.isEmpty()) {
            return Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        ITypedIngredient<Object> typed = (ITypedIngredient<Object>) ingredient;
        IIngredientRenderer<Object> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        return List.copyOf(renderer.getTooltip(typed.getIngredient(), TooltipFlag.Default.NORMAL));
    }

    private static ItemStack extractItemStack(@Nullable ITypedIngredient<?> ingredient) {
        return ingredient == null
                ? ItemStack.EMPTY
                : ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private @Nullable IIngredientManager getIngredientManager() {
        var runtime = com.lhy.jeict.jei.JeiCraftingTreePlugin.getJeiRuntime();
        return runtime == null ? null : runtime.getIngredientManager();
    }

    private FloatingMaterialOverlayState.Snapshot createFloatingMaterialSnapshot() {
        List<FloatingMaterialOverlayState.Group> groups = new ArrayList<>();
        collectFloatingMaterialGroups(context.root(), batchCount, groups);
        return new FloatingMaterialOverlayState.Snapshot(groups, context);
    }

    private void collectFloatingMaterialGroups(RecipeTreeNodeViewModel node, int crafts,
            List<FloatingMaterialOverlayState.Group> groups) {
        List<FloatingMaterialOverlayState.Entry> entries = new ArrayList<>();
        Map<RecipeTreeNodeViewModel, Integer> childRequirements = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            int amount = Math.max(1, safeMultiply(crafts, input.amount()));
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                childRequirements.merge(child, amount, RecipeTreeOverviewScreen::safeAdd);
                ItemStack output = child.recipe().primaryOutput().copyWithCount(amount);
                if (!output.isEmpty()) {
                    entries.add(new FloatingMaterialOverlayState.Entry(output, amount));
                }
                continue;
            }
            ItemStack stack = input.displayStack();
            if (stack.isEmpty()) {
                RequestedIngredient requested = input.selectedRequestedIngredient();
                if (requested != null && !requested.alternatives().isEmpty()) {
                    stack = requested.alternatives().getFirst();
                }
            }
            if (!stack.isEmpty()) {
                entries.add(new FloatingMaterialOverlayState.Entry(stack.copyWithCount(amount), amount));
            }
        }
        for (Map.Entry<RecipeTreeNodeViewModel, Integer> childRequirement : childRequirements.entrySet()) {
            RecipeTreeNodeViewModel child = childRequirement.getKey();
            int childCrafts = ceilDiv(childRequirement.getValue(), child.recipe().primaryOutputCount());
            collectFloatingMaterialGroups(child, childCrafts, groups);
        }
        if (!entries.isEmpty()) {
            groups.add(new FloatingMaterialOverlayState.Group(node.recipe().title(), entries));
        }
    }

    private <T> void renderJeiIngredientTyped(GuiGraphics graphics, IIngredientManager ingredientManager, ITypedIngredient<?> ingredient,
            int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        renderer.render(graphics, typed.getIngredient(), x, y);
    }

    private void syncComputeQuantitiesButton() {
        if (!settingsOpen) {
            computeQuantitiesButton.setMessage(Component.empty());
            return;
        }
        computeQuantitiesButton.setMessage(Component.translatable(computeRecipeQuantities
                ? "gui.jeict.recipe_tree.overview_quantity_compute_enabled"
                : "gui.jeict.recipe_tree.overview_quantity_compute_disabled"));
    }

    private void syncToggleExistingPatternButton() {
        if (!settingsOpen) {
            toggleExistingPatternButton.setMessage(Component.empty());
            return;
        }
        toggleExistingPatternButton.setMessage(Component.translatable(context.disableExistingPatternExpansion()
                ? "gui.jeict.recipe_tree.overview_toggle_existing_short_disabled"
                : "gui.jeict.recipe_tree.overview_toggle_existing_short_enabled"));
    }

    private void syncAutoUniqueRecipeButton() {
        if (!settingsOpen) {
            autoUniqueRecipeButton.setMessage(Component.empty());
            return;
        }
        autoUniqueRecipeButton.setMessage(Component.translatable(autoExpandUniqueEncodableRecipe
                ? "gui.jeict.recipe_tree.overview_auto_unique_short_enabled"
                : "gui.jeict.recipe_tree.overview_auto_unique_short_disabled"));
    }

    private void syncMemoryReadingButton() {
        if (!settingsOpen) {
            memoryReadingButton.setMessage(Component.empty());
            return;
        }
        memoryReadingButton.setMessage(Component.translatable(readRememberedSelections
                ? "gui.jeict.recipe_tree.overview_memory_reading_enabled"
                : "gui.jeict.recipe_tree.overview_memory_reading_disabled"));
    }

    private void syncAutoMergeButton() {
        if (!settingsOpen) {
            autoMergeButton.setMessage(Component.empty());
            return;
        }
        autoMergeButton.setMessage(autoMergeSameMaterials
                ? Component.translatable("gui.jeict.recipe_tree.overview_merge_enabled")
                : Component.translatable("gui.jeict.recipe_tree.overview_merge_disabled"));
    }

    private void syncStyleButton() {
        styleButton.setMessage(settingsOpen ? RecipeTreeTheme.styleButtonMessage() : Component.empty());
        topMaterialRenderCacheDirty = true;
    }

    private void markAutoExpandUniqueDirty() {
        autoExpandUniqueSearchPending = autoExpandUniqueEncodableRecipe;
        autoExpandUniqueCandidateCache.clear();
    }

    private void uploadPatterns() {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsUpload()) {
            return;
        }
        List<RecipeTreeRecipeViewModel> recipes = collectEncodableRecipes();
        if (recipes.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_no_patterns"), true);
            }
            return;
        }
        if (backend.uploadPatterns(recipes)) {
            this.onClose();
        }
    }

    private void encodePatterns() {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsEncode()) {
            return;
        }
        List<RecipeTreeRecipeViewModel> recipes = collectEncodableRecipes();
        if (recipes.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_no_patterns"), true);
            }
            return;
        }
        if (backend.encodePatterns(recipes)) {
            this.onClose();
        }
    }

    private List<RecipeTreeRecipeViewModel> collectEncodableRecipes() {
        List<RecipeTreeRecipeViewModel> recipes = new ArrayList<>();
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (hasExistingPatternForOutput(recipe)) {
                continue;
            }
            if (recipe.primaryOutputIngredient() != null) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private int computeRequiredPatternCountUncached(List<RecipeTreeRecipeViewModel> selectedRecipes) {
        int count = 0;
        for (RecipeTreeRecipeViewModel recipe : selectedRecipes) {
            if (!hasExistingPatternForOutput(recipe) && recipe.primaryOutputIngredient() != null) {
                count++;
            }
        }
        return count;
    }

    private boolean hasExistingPatternForOutput(RecipeTreeRecipeViewModel recipe) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsExistingPatternHints()
                || recipe == null || recipe.primaryOutputIngredient() == null) {
            return false;
        }
        return existingPatternRecipeCache.computeIfAbsent(recipe,
                ignored -> backend.isCraftable(recipe.primaryOutputIngredient().getIngredient()));
    }

    private void collapseExpandedExistingPatternNodes(RecipeTreeNodeViewModel parent) {
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                continue;
            }
            if (hasExistingPatternForOutput(child.recipe())) {
                context.forgetSelection(signatureOf(input));
                input.setChild(null);
                continue;
            }
            collapseExpandedExistingPatternNodes(child);
        }
    }

    private int inventoryAdjustedLabelColor(RequestedIngredient material, int requiredCount) {
        if (!computeRecipeQuantities || material == null) {
            return RecipeTreeTheme.current().mutedText();
        }
        int available = getAvailableCount(material);
        if (available >= Math.max(1, requiredCount)) {
            return RecipeTreeTheme.current().enough();
        }
        return available <= 0 ? RecipeTreeTheme.current().missing() : RecipeTreeTheme.current().partial();
    }

    private int getAvailableCount(RequestedIngredient material) {
        if (material == null || material.alternatives().isEmpty()) {
            return 0;
        }
        int maxAvailable = 0;
        for (ItemStack alternative : material.alternatives()) {
            maxAvailable = Math.max(maxAvailable, getItemCountInInventory(alternative));
        }
        return maxAvailable;
    }

    private static int getItemCountInInventory(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Inventory inventory = minecraft.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, invStack)) {
                count = safeAdd(count, invStack.getCount());
            }
        }
        return count;
    }

    private static String formatCompactCount(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        double value = count;
        String[] suffixes = { "K", "M", "B" };
        int suffixIndex = -1;
        while (value >= 1000.0D && suffixIndex + 1 < suffixes.length) {
            value /= 1000.0D;
            suffixIndex++;
        }
        if (suffixIndex < 0) {
            return Integer.toString(count);
        }
        if (value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D) {
            return ((int) Math.round(value)) + suffixes[suffixIndex];
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, suffixes[suffixIndex]);
    }

    private static boolean isPointInsideButton(Button button, double mouseX, double mouseY) {
        return button != null && button.visible
                && mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();
    }

    private boolean isPointInsideBatchBadge(double mouseX, double mouseY) {
        updateBatchBadgeBounds();
        return batchBadgeBounds != null && batchBadgeBounds.contains(mouseX, mouseY);
    }

    /** Invisible vanilla button used only for focus, narration and hit testing. */
    private static final class ChromeButton extends Button {
        private ChromeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // painted by RecipeTreeOverviewScreen.renderAe2Button
        }
    }

    private static Button chromeButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        return new ChromeButton(x, y, width, height, message, onPress);
    }


    private static int safeMultiply(int left, int right) {
        long value = (long) left * (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int safeAdd(int left, int right) {
        long value = (long) left + (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            return numerator;
        }
        long value = ((long) numerator + (long) denominator - 1L) / (long) denominator;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private record GraphNode(@Nullable ITypedIngredient<?> ingredient, String title, String amount, String exactAmount,
            @Nullable RecipeTreeNodeViewModel recipeNode, @Nullable IDrawable machineIcon,
            @Nullable Component machineName, @Nullable MergedLeaf mergedLeaf, boolean showsPatternHint,
            List<GraphNode> children, int width) {
        private Component titleComponent() {
            return Component.literal(title);
        }
    }

    private record MergedLeaf(@Nullable ITypedIngredient<?> ingredient, String title, int totalAmount, String sourceAmountText,
            RecipeTreeNodeViewModel parentNode, List<RecipeTreeInputViewModel> members,
            List<RecipeTreeNodeViewModel> memberParents) {
        private MergedLeaf {
            memberParents = memberParents == null ? List.of() : List.copyOf(memberParents);
        }

        private RecipeTreeNodeViewModel parentForMember(int index) {
            if (!memberParents.isEmpty() && index < memberParents.size()) {
                return memberParents.get(index);
            }
            return parentNode;
        }

        private MergedLeaf withAddedAmount(int addedAmount, RecipeTreeInputViewModel member) {
            List<RecipeTreeInputViewModel> nextMembers = new ArrayList<>(members);
            nextMembers.add(member);
            List<RecipeTreeNodeViewModel> nextParents = new ArrayList<>(memberParents);
            if (!nextParents.isEmpty()) {
                nextParents.add(parentNode);
            }
            return new MergedLeaf(ingredient, title, safeAdd(totalAmount, Math.max(1, addedAmount)), sourceAmountText, parentNode,
                    List.copyOf(nextMembers), List.copyOf(nextParents));
        }

        private RecipeTreeInputViewModel representative() {
            return members.getFirst();
        }
    }

    private record PositionedNode(GraphNode graph, int x, int y) {
    }

    private record Edge(PositionedNode parent, PositionedNode child) {
    }

    private record PendingJeiSelection(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            List<RecipeTreeNodeViewModel> recipeApplyBatch) {
        private PendingJeiSelection {
            recipeApplyBatch = recipeApplyBatch == null ? List.of() : List.copyOf(recipeApplyBatch);
        }
    }

    private record PendingAlternativeSelection(List<RecipeTreeInputViewModel> members, List<DisplayOption> alternatives,
            int selectedAlternativeIndex, int anchorX, int anchorY) {
    }

    private record TopMaterialBounds(RequestedIngredient material, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record TopMaterialRenderData(RequestedIngredient material, int displayCount, int labelColor,
            String label, int width, ItemStack stack, boolean showsPatternHint) {
    }

    private record TopMaterialsPinButtonBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record BatchBadgeBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record RootBatchAnchor(int left, int right, int y) {
        private int width() {
            return Math.max(1, right - left);
        }
    }

    private record AlternativeButtonBounds(@Nullable MergedLeaf leaf, @Nullable RequestedIngredient material, int x, int y, int width, int height) {
        private static AlternativeButtonBounds forLeaf(MergedLeaf leaf, int x, int y, int width, int height) {
            return new AlternativeButtonBounds(leaf, null, x, y, width, height);
        }

        private static AlternativeButtonBounds forMaterial(RequestedIngredient material, int x, int y, int width, int height) {
            return new AlternativeButtonBounds(null, material, x, y, width, height);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record AlternativeOptionBounds(int index, @Nullable ITypedIngredient<?> ingredient, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record LayerRowRenderCache(int[] leftOffsets, int[] rightOffsets, int[] centerOffsets, int width,
            int anchorCenterOffset) {
    }

    private record GenericTopMaterialRenderData(MergedLeaf leaf, String label, int width) {
    }

    private record GenericTopMaterialBounds(GenericTopMaterialRenderData data, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static final class ItemTopMaterialAccumulator {
        private final List<ItemStack> alternatives;
        private final List<UnresolvedInputSlot> slots = new ArrayList<>();
        private int totalAmount;

        private ItemTopMaterialAccumulator(RecipeTreeInputViewModel input) {
            alternatives = List.copyOf(input.orderedAlternativesView());
        }

        private void add(RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parent, int amount) {
            totalAmount = safeAdd(totalAmount, Math.max(1, amount));
            slots.add(new UnresolvedInputSlot(input, parent));
        }

        private RequestedIngredient toRequestedIngredient() {
            return new RequestedIngredient(alternatives, Math.max(1, totalAmount));
        }

        private List<UnresolvedInputSlot> slots() {
            return List.copyOf(slots);
        }
    }

    private final class GenericTopMaterialAccumulator {
        private final List<RecipeTreeInputViewModel> members = new ArrayList<>();
        private final List<RecipeTreeNodeViewModel> parents = new ArrayList<>();
        private int totalAmount;

        private GenericTopMaterialAccumulator(RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parent) {
        }

        private void add(RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parent, int amount) {
            members.add(input);
            parents.add(parent);
            totalAmount = safeAdd(totalAmount, Math.max(1, amount));
        }

        private MergedLeaf toLeaf() {
            RecipeTreeInputViewModel representative = members.getFirst();
            return new MergedLeaf(resolveGroupIngredient(members), displayNameOf(representative), totalAmount,
                    representative.amountText(), parents.getFirst(), List.copyOf(members), List.copyOf(parents));
        }

        private List<UnresolvedInputSlot> slots() {
            List<UnresolvedInputSlot> slots = new ArrayList<>(members.size());
            for (int i = 0; i < members.size(); i++) {
                slots.add(new UnresolvedInputSlot(members.get(i), parents.get(i)));
            }
            return List.copyOf(slots);
        }
    }

    private static final class MergedBuildStats {
        int collectCalls;
        int recipeNodeAdds;
        int leafAdds;
        int aggregatedChildLinks;
        int layerRows;
        int layerMaterials;
    }
}

