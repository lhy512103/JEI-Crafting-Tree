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
import org.slf4j.Logger;

import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeOutputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.lhy.jeict.recipe_tree.RecipeTreeSearchIndex;
import com.lhy.jeict.api.CraftingTreeBackend;
import com.lhy.jeict.api.CraftingTreeBackends;
import com.lhy.jeict.api.PatternEncodingDraft;
import com.lhy.jeict.api.PatternEncodingMode;
import com.lhy.jeict.api.PatternEncodingRequest;
import com.lhy.jeict.api.PatternEncodingSlot;
import com.lhy.jeict.debug.RecipeTreePerfDebug;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.jei.RecipeTreeOpenHelper;
import com.lhy.jeict.client.RecipeTreeWorkspaceSession.Direction;
import com.lhy.jeict.client.RecipeTreeWorkspaceSession.GridPosition;
import com.lhy.jeict.recipe_tree.RequestedIngredient;
import com.lhy.jeict.util.GenericIngredientUtil;
import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.RecipePlanResult;
import com.lhy.jeict.planning.PlanTarget;
import com.lhy.jeict.planning.RecipeTreePlanAdapter;
import com.lhy.jeict.planning.SubstitutionStrategy;
import com.lhy.jeict.compat.JustEnoughCharactersCompat;
import com.lhy.jeict.config.RecipeTreeConfig;
import com.mojang.logging.LogUtils;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;

public class RecipeTreeOverviewScreen extends Screen implements RecipeTreeJeiTransferTarget {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NODE_HEIGHT = 28;
    private static final int NODE_MAIN_SIZE = 28;
    private static final int NODE_PART_GAP = 2;
    private static final int NODE_PART_PADDING = 2;
    private static final int LEVEL_GAP = 46;
    private static final int LEAF_GAP = 6;
    private static final int VISIBILITY_MARGIN = 32;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 34;
    private static final double INITIAL_MIN_ZOOM = 0.7D;
    private static final double INITIAL_MAX_ZOOM = 1.0D;
    private static final int MACHINE_SLOT_SIZE = 18;
    /** 与样板编码终端/JEI 开关同源图标（8×8 纹理缩放绘制） */
    private static final int TOP_MATERIALS_OFFSET = 100;
    private static final int INSPECTOR_WIDTH = 216;
    private static final int INSPECTOR_SCROLL_STEP = 18;
    private static final long SCREEN_NOTICE_DURATION_MS = 10_000L;
    private static final int SETTINGS_WIDTH = 148;
    private static final int SETTINGS_COLLAPSED_WIDTH = 34;
    private static final int BATCH_TEXT_GAP = 3;
    private static final boolean AE2_LOADED = ModList.get().isLoaded("ae2");
    private static final ResourceLocation AE2_PATTERN_MODES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/pattern_modes.png");
    private static final ResourceLocation AE2_STATES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");
    private static final int AE2_PATTERN_PANEL_SRC_X = 0;
    private static final int AE2_CRAFTING_PANEL_SRC_Y = 0;
    private static final int AE2_PROCESSING_PANEL_SRC_Y = 70;
    private static final int AE2_SMITHING_PANEL_SRC_X = 128;
    private static final int AE2_SMITHING_PANEL_SRC_Y = 70;
    private static final int AE2_STONECUTTING_PANEL_SRC_Y = 140;
    private static final int AE2_STONECUTTING_SELECTED_SRC_X = 124;
    private static final int AE2_STONECUTTING_SELECTED_SRC_Y = 162;
    private static final int AE2_PATTERN_PANEL_WIDTH = 124;
    private static final int AE2_PATTERN_PANEL_HEIGHT = 66;
    private static final ResourceLocation AE2_SMALL_SCROLLER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("ae2", "small_scroller");
    private static final ResourceLocation AE2_SMALL_SCROLLER_DISABLED_SPRITE =
            ResourceLocation.fromNamespaceAndPath("ae2", "small_scroller_disabled");

    private RecipeTreeRootContext context;
    private final Screen returnScreen;
    private final RecipeTreeWorkspaceSession workspace;
    private final List<WorkspaceLinkBounds> workspaceLinkBounds = new ArrayList<>();
    /** Independent full-screen tree controllers rendered together on one shared canvas. */
    private final Map<RecipeTreeRootContext, RecipeTreeOverviewScreen> workspaceTreeScreens = new IdentityHashMap<>();
    private @Nullable RecipeTreeOverviewScreen workspaceHost;
    private @Nullable RecipeTreeOverviewScreen focusedWorkspaceTree;
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
    private final Set<RecipeTreeInputViewModel> cycleBlockedInputs = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<RecipeTreeInputViewModel, CachedCollapsedState> collapsedStateCache = new IdentityHashMap<>();
    private boolean cycleWarningsChanged;
    private final Map<RecipeTreeInputViewModel, CachedLayerMaterialKey> leafLayerKeyCache = new IdentityHashMap<>();
    private final Map<RecipeTreeNodeViewModel, LayerMaterialKey> recipeLayerKeyCache = new IdentityHashMap<>();
    /** Reuses saturated quantity propagation results across the structure/top-material passes of one rebuild. */
    private final QuantityPropagationCache quantityPropagationCache = new QuantityPropagationCache();
    private final Map<ITypedIngredient<?>, String> typedIngredientSignatureCache = new IdentityHashMap<>();
    private final List<TopMaterialBounds> topMaterialBounds = new ArrayList<>();
    private final List<TopMaterialBounds> surplusMaterialBounds = new ArrayList<>();
    private final List<GenericTopMaterialBounds> genericTopMaterialBounds = new ArrayList<>();
    private final List<AlternativeButtonBounds> alternativeButtonBounds = new ArrayList<>();
    private final List<AlternativeOptionBounds> alternativeOptionBounds = new ArrayList<>();
    private final List<LayerMaterialBounds> layerMaterialBounds = new ArrayList<>();
    /** Only the currently visible 9 input and 3 output slots receive hit boxes. */
    private final List<PatternDraftSlotBounds> inspectorPatternSlotBounds = new ArrayList<>(12);
    private final List<PatternControlBounds> inspectorPatternControlBounds = new ArrayList<>(8);
    private Map<String, PatternEncodingDraft> patternDrafts;
    /** JEI layouts are expensive and runtime-bound; create at most once per inspected source recipe. */
    private final Map<String, Optional<IRecipeLayoutDrawable<?>>> jeiRecipePreviewCache = new HashMap<>();
    private Map<String, RecipeTreeRecipeViewModel> patternDraftSourceRecipes;
    private Set<String> modifiedPatternRecipeKeys;
    /** Recipe nodes keep their identity when a pattern draft replaces the recipe view model. */
    private Set<RecipeTreeNodeViewModel> modifiedPatternNodes;
    private int patternDraftScroll;
    private int inspectorScroll;
    private int inspectorMaxScroll;
    private int inspectorViewportTop;
    private int inspectorViewportBottom;
    private int inspectorScrollbarTrackTop;
    private int inspectorScrollbarTrackHeight;
    private int inspectorScrollbarThumbTop;
    private int inspectorScrollbarThumbHeight;
    private boolean draggingInspectorScrollbar;
    private double inspectorScrollbarDragOffset;
    private @Nullable String lastInspectorScrollIdentity;
    private @Nullable String lastInspectorDraftIdentity;
    private @Nullable EditBox patternAmountEditor;
    private @Nullable ScreenNotice screenNotice;
    private @Nullable PatternDraftSlotBounds patternAmountEditTarget;
    private int patternAmountEditorX = 8;
    private int patternAmountEditorY = 8;
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
    private Button itemSubstitutionButton;
    private Button fluidSubstitutionButton;
    private Button encodeButton;
    private Button uploadButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button fitViewButton;
    private Button settingsButton;
    private Button projectsButton;
    private Button planReportButton;
    private Button undoButton;
    private Button redoButton;
    private Button strategyButton;
    private EditBox searchBox;
    private final Map<RecipeTreeRootContext, RecipeTreeHistory> workspaceHistories = new IdentityHashMap<>();
    private RecipeTreeHistory history;
    private final AsyncRecipePlanService planService = new AsyncRecipePlanService();
    private RecipePlanResult planningResult = new RecipePlanResult(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Set.of());
    /** Temporary diagnostics for the intermittent case where the screen stays open but its tree vanishes. */
    private int diagnosticEmptyRenderFrames;
    private boolean diagnosticRenderedTreeOnce;
    private int diagnosticLastStructureCount = -1;
    private List<PlanTarget> planningTargets = List.of();
    private long planningInventoryVersion = Long.MIN_VALUE;
    private long planningFingerprint = Long.MIN_VALUE;
    private boolean planningBusy;
    private int searchResultIndex = -1;
    private final Set<PositionedNode> focusedSearchPath = new HashSet<>();
    private @Nullable BatchBadgeBounds batchBadgeBounds;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private double visibleLogicalMinX;
    private double visibleLogicalMaxX;
    private double visibleLogicalMinY;
    private double visibleLogicalMaxY;
    private boolean initializedPan;
    private boolean initializedWorkspaceView;
    private int alternativeScroll;
    private int toolbarLeft;
    private int headerTitleLeft = 222;
    private int headerTitleRight = 222;
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
        this(context, returnScreen, new RecipeTreeWorkspaceSession(context));
    }

    public RecipeTreeOverviewScreen(RecipeTreeRootContext context, Screen returnScreen,
            RecipeTreeWorkspaceSession workspace) {
        super(Component.translatable("gui.jeict.recipe_tree.overview_title"));
        this.context = context;
        this.returnScreen = returnScreen;
        this.workspace = workspace;
        this.workspace.activate(context);
        RecipeTreeOpenHelper.rememberWorkspace(workspace);
        this.patternDrafts = context.patternDrafts();
        this.patternDraftSourceRecipes = context.patternDraftSourceRecipes();
        this.modifiedPatternRecipeKeys = context.modifiedPatternRecipeKeys();
        this.modifiedPatternNodes = context.modifiedPatternNodes();
        this.history = workspaceHistories.computeIfAbsent(context, ignored -> new RecipeTreeHistory());
        this.readRememberedSelections = RecipeTreeConfig.REMEMBER_SELECTIONS.get()
                && RecipeTreeClientMemory.isMemoryReadingEnabled();
        this.autoMergeSameMaterials = RecipeTreeConfig.AUTO_MERGE_MATERIALS.get();
        this.computeRecipeQuantities = RecipeTreeConfig.COMPUTE_QUANTITIES.get();
        this.autoExpandUniqueEncodableRecipe = RecipeTreeConfig.AUTO_EXPAND_UNIQUE_RECIPES.get();
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = chromeButton(8, 8, 50, 20,
                Component.translatable("gui.jeict.recipe_tree.back"), btn -> onClose());
        this.toggleExistingPatternButton = chromeButton(this.width - 220, 8, 86, 18, Component.empty(),
                btn -> focusedTree().toggleExistingPatternExpansion());
        this.autoUniqueRecipeButton = chromeButton(this.width - 296, 8, 72, 18, Component.empty(),
                btn -> {
                    autoExpandUniqueEncodableRecipe = !autoExpandUniqueEncodableRecipe;
                    RecipeTreeConfig.AUTO_EXPAND_UNIQUE_RECIPES.set(autoExpandUniqueEncodableRecipe);
                    markAutoExpandUniqueDirty();
                    syncAutoUniqueRecipeButton();
                    rebuildLayout();
                });
        this.memoryReadingButton = chromeButton(this.width - 372, 8, 72, 18, Component.empty(),
                btn -> {
                    readRememberedSelections = !readRememberedSelections;
                    rememberedSelectionsDirty = readRememberedSelections;
                    RecipeTreeConfig.REMEMBER_SELECTIONS.set(readRememberedSelections);
                    RecipeTreeClientMemory.setMemoryReadingEnabled(readRememberedSelections);
                    syncMemoryReadingButton();
                    rebuildLayout();
                });
        this.computeQuantitiesButton = chromeButton(this.width - 372, 8, 64, 18, Component.empty(),
                btn -> {
                    computeRecipeQuantities = !computeRecipeQuantities;
                    RecipeTreeConfig.COMPUTE_QUANTITIES.set(computeRecipeQuantities);
                    syncComputeQuantitiesButton();
                    refreshRenderedProjection();
                });
        this.autoMergeButton = chromeButton(this.width - 148, 8, 68, 18, Component.empty(),
                btn -> {
                    RecipeTreeOverviewScreen target = focusedTree();
                    target.autoMergeSameMaterials = !target.autoMergeSameMaterials;
                    RecipeTreeConfig.AUTO_MERGE_MATERIALS.set(target.autoMergeSameMaterials);
                    target.rebuildLayout();
                    syncAutoMergeButton();
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
                Component.translatable("gui.jeict.recipe_tree.overview_fit"), btn -> fitFocusedTreeView());
        this.settingsButton = chromeButton(this.width - 34, 8, 26, 20, Component.literal("⋮"), btn -> {
            settingsOpen = !settingsOpen;
            updateSelectionButtons();
        });
        this.searchBox = new EditBox(this.font, 64, 9, 150, 18, Component.translatable("gui.jeict.recipe_tree.search"));
        this.searchBox.setMaxLength(96);
        this.searchBox.setHint(Component.translatable("gui.jeict.recipe_tree.search_hint"));
        this.searchBox.setResponder(value -> {
            searchResultIndex = -1;
            focusedSearchPath.clear();
            selectedNode = null;
        });
        this.projectsButton = chromeButton(this.width - 520, 8, 58, 20, Component.translatable("gui.jeict.recipe_tree.projects"),
                btn -> focusedTree().openProjects());
        this.planReportButton = chromeButton(this.width - 456, 8, 52, 20,
                Component.translatable("gui.jeict.recipe_tree.plan"), btn -> focusedTree().openPlanReport());
        this.undoButton = chromeButton(this.width - 590, 8, 28, 20, Component.empty(), btn -> focusedTree().undoLastEdit());
        this.redoButton = chromeButton(this.width - 620, 8, 28, 20, Component.empty(), btn -> focusedTree().redoLastEdit());
        this.strategyButton = chromeButton(8, HEADER_HEIGHT + 8, 120, 24, Component.empty(), btn -> cycleSubstitutionStrategy());
        this.itemSubstitutionButton = chromeButton(this.width / 2 - 230, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.item_substitution"), btn -> focusedTree().togglePatternSubstitution(false));
        this.fluidSubstitutionButton = chromeButton(this.width / 2 - 152, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.fluid_substitution"), btn -> focusedTree().togglePatternSubstitution(true));
        this.encodeButton = chromeButton(this.width / 2 - 74, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.encode"), btn -> encodeWorkspacePatterns());
        this.uploadButton = chromeButton(this.width / 2 + 4, this.height - 26, 70, 20,
                Component.translatable("gui.jeict.recipe_tree.upload"), btn -> uploadWorkspacePatterns());
        this.patternAmountEditor = new EditBox(this.font, 0, 0, 88, 18,
                Component.translatable("gui.jeict.recipe_tree.pattern_amount"));
        this.patternAmountEditor.setMaxLength(19);
        this.patternAmountEditor.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.patternAmountEditor.visible = false;

        this.addRenderableWidget(backButton);
        this.addRenderableWidget(searchBox);
        this.addRenderableWidget(projectsButton);
        this.addRenderableWidget(planReportButton);
        this.addRenderableWidget(undoButton);
        this.addRenderableWidget(redoButton);
        this.addRenderableWidget(strategyButton);
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
        this.addRenderableWidget(itemSubstitutionButton);
        this.addRenderableWidget(fluidSubstitutionButton);
        this.addRenderableWidget(encodeButton);
        this.addRenderableWidget(uploadButton);
        this.addRenderableWidget(patternAmountEditor);
        syncComputeQuantitiesButton();
        syncStyleButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncMemoryReadingButton();
        syncAutoMergeButton();
        syncStrategyButton();
        updateSelectionButtons();
        rebuildLayout();
        if (workspaceHost == null) {
            if (focusedWorkspaceTree == null) focusedWorkspaceTree = this;
            syncWorkspaceTreeScreens();
            if (!initializedWorkspaceView) fitWorkspaceView();
        }
    }


    @Override
    public void tick() {
        super.tick();
        tickTreeState(true);
        if (workspaceHost == null) {
            syncWorkspaceTreeScreens();
            for (RecipeTreeOverviewScreen tree : workspaceTreeScreens.values()) {
                tree.tickTreeState(false);
            }
        }
    }

    private void tickTreeState(boolean pollSharedBackend) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (pollSharedBackend && backend != null && backend.pollExistingPatternCachesStale()) {
            refreshCraftableDependentCaches();
            if (workspaceHost == null) {
                for (RecipeTreeOverviewScreen tree : workspaceTreeScreens.values()) {
                    tree.refreshCraftableDependentCaches();
                }
            }
        }
        processAutoExpandUniqueRecipeSteps(RecipeTreeConfig.MAX_AUTO_EXPAND_STEPS_PER_TICK.get());
        long inventoryVersion = ClientInventorySnapshotCache.version();
        if (inventoryVersion != planningInventoryVersion) requestPlanning();
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
        if (context.disableExistingPatternExpansion() && collapseExpandedExistingPatternNodes(context.root())) {
            closeSelection();
            rebuildLayout();
            RecipeTreePerfDebug.logPhase("refresh_craftable_dependent_collapse", startedAt, "existing-pattern=true");
            return;
        }
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
        if (changedHints > 0) {
            mergedLayerRows = List.copyOf(rebuilt);
            refreshMergedContentWidth();
            rebuildMergedRowRenderCaches();
        }
        RecipeTreePerfDebug.logPhase("refresh_craftable_dependent", startedAt,
                "merge={} rows={} changedHints={} requiredPatterns={}",
                autoMergeSameMaterials, mergedLayerRows.size(), changedHints, cachedRequiredPatternCount);
    }

    private void rebuildLayoutStructureCore() {
        long startedAt = RecipeTreePerfDebug.begin();
        long structureBuildStartedAt = RecipeTreePerfDebug.begin();
        RootBatchAnchor previousRootAnchor = initializedPan ? findRootBatchAnchor() : null;
        double previousRootScreenX = previousRootAnchor == null ? 0.0D
                : (previousRootAnchor.left() + previousRootAnchor.right()) * 0.5D * zoom + panX;
        double previousRootScreenY = previousRootAnchor == null ? 0.0D
                : previousRootAnchor.y() * zoom + panY;
        quantityPropagationCache.clear();
        if (readRememberedSelections && rememberedSelectionsDirty) {
            autoApplyRememberedChildren(context.root());
            rememberedSelectionsDirty = false;
        }
        requestPlanning();
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
            this.rootNode = buildGraph(context.root(), batchCount, false);
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
        if (previousRootAnchor != null) {
            RootBatchAnchor rebuiltRootAnchor = findRootBatchAnchor();
            if (rebuiltRootAnchor != null) {
                double rebuiltRootCenterX = (rebuiltRootAnchor.left() + rebuiltRootAnchor.right()) * 0.5D;
                panX += previousRootScreenX - (rebuiltRootCenterX * zoom + panX);
                panY += previousRootScreenY - (rebuiltRootAnchor.y() * zoom + panY);
            }
        }
        RecipeTreePerfDebug.logPhase("rebuild_structure", startedAt,
                "merge={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
        logStructureTransition();
    }

    private void logStructureTransition() {
        int structureCount = autoMergeSameMaterials ? mergedLayerRows.size() : positionedNodes.size();
        if (structureCount == diagnosticLastStructureCount) return;
        int previous = diagnosticLastStructureCount;
        diagnosticLastStructureCount = structureCount;
        LOGGER.info("[JEICT-TREE] structure changed previous={} current={} merge={} nodes={} edges={} rows={} root={} initializedPan={} pan=({}, {}) zoom={}",
                previous, structureCount, autoMergeSameMaterials, positionedNodes.size(), edges.size(),
                mergedLayerRows.size(), context.root().recipe().stableIdentity(), initializedPan,
                Math.round(panX * 100.0D) / 100.0D, Math.round(panY * 100.0D) / 100.0D,
                Math.round(zoom * 1000.0D) / 1000.0D);
        if (previous > 0 && structureCount == 0) {
            LOGGER.error("[JEICT-TREE] non-empty tree structure became empty; caller trace follows",
                    new IllegalStateException("JEICT temporary empty tree structure trace"));
        }
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
            cycleWarningsChanged = false;
            int applied = 0;
            for (int step = 0; step < Math.max(1, maxSteps); step++) {
                int appliedThisPass = tryAutoExpandUniqueEncodableRecipeSinglePass();
                if (appliedThisPass <= 0) {
                    break;
                }
                mutated = true;
                applied += appliedThisPass;
            }
            if (mutated || cycleWarningsChanged) {
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
        collectUnresolvedInputsGrouped(context.root(), grouped,
                java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
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
                markCycleBlocked(leaf);
                continue;
            }
            applyLeafSelection(leaf, chosen.get(), rememberedCache);
            applied++;
        }
        return applied;
    }

    private void collectUnresolvedInputsGrouped(RecipeTreeNodeViewModel node,
            Map<String, List<UnresolvedInputSlot>> grouped, Set<RecipeTreeNodeViewModel> visited) {
        if (!visited.add(node)) {
            return;
        }
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                grouped.computeIfAbsent(signatureOf(input), k -> new ArrayList<>()).add(new UnresolvedInputSlot(input, node));
            } else {
                collectUnresolvedInputsGrouped(child, grouped, visited);
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

    private void markCycleBlocked(MergedLeaf leaf) {
        for (RecipeTreeInputViewModel input : leaf.members()) {
            cycleWarningsChanged |= cycleBlockedInputs.add(input);
        }
    }

    private void markCycleBlocked(RecipeTreeNodeViewModel node) {
        RecipeTreeNodeViewModel parent = node.parent();
        if (parent == null) return;
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() == node) {
                cycleWarningsChanged |= cycleBlockedInputs.add(input);
            }
        }
    }

    private void clearCycleBlocked(RecipeTreeNodeViewModel node) {
        RecipeTreeNodeViewModel parent = node.parent();
        if (parent == null) return;
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() == node) cycleBlockedInputs.remove(input);
        }
    }

    private boolean hasCycleWarning(List<RecipeTreeInputViewModel> inputs) {
        return inputs.stream().anyMatch(cycleBlockedInputs::contains);
    }

    private boolean hasCycleWarning(RequestedIngredient material) {
        List<UnresolvedInputSlot> slots = unresolvedInputsBySignature.get(signatureOf(material));
        return slots != null && slots.stream().anyMatch(slot -> cycleBlockedInputs.contains(slot.input()));
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
            if (recipe.inputs().isEmpty() || !isRecipeSnapshotStrictEncodable(recipe)) {
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

    private record CachedInputSignature(int alternativeIndex, String signature) {
    }

    private record CachedCollapsedState(RecipeTreeNodeViewModel parent, int inputIndex, String signature,
            boolean collapsed) {
    }

    private static final class QuantityPropagationCache {
        private final IdentityHashMap<RecipeTreeInputViewModel, Map<Integer, Integer>> amounts = new IdentityHashMap<>();

        private int amount(RecipeTreeInputViewModel input, int crafts) {
            Map<Integer, Integer> byCrafts = amounts.computeIfAbsent(input, ignored -> new HashMap<>());
            return byCrafts.computeIfAbsent(crafts, value -> Math.max(1, safeMultiply(value, input.amount())));
        }

        private void clear() {
            amounts.clear();
        }
    }

    private record LayerTraversalKey(RecipeTreeNodeViewModel node, int depth) {
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
        rebuildMergedRowRenderCaches();
    }

    private void rebuildMergedRowRenderCaches() {
        mergedRowRenderCaches.clear();
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

    private GraphNode buildGraph(RecipeTreeNodeViewModel node, int crafts, boolean cycleWarning) {
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
                int amount = quantityPropagationCache.amount(input, crafts);
                totalRequiredAmount = safeAdd(totalRequiredAmount, Math.max(1, amount));
            }

            RecipeTreeNodeViewModel child = representative.child();

            if (child != null) {
                int childCrafts = ceilDiv(totalRequiredAmount, child.recipe().primaryOutputCount());
                children.add(buildGraph(child, childCrafts, hasCycleWarning(group)));
            } else {
                addMergedLeafChild(node, children, group, totalRequiredAmount);
            }
        }

        String amountLabel = "";
        String exactAmountLabel = "";
        if (computeRecipeQuantities) {
            ITypedIngredient<?> outputIngredient = node.recipe().primaryOutputIngredient();
            int producedAmount = safeMultiply(crafts, node.recipe().primaryOutputCount());
            if (isMekanismChemical(outputIngredient)) {
                amountLabel = formatCompactChemicalAmount(producedAmount);
                exactAmountLabel = formatChemicalAmount(producedAmount);
            } else if (isMilliBucketAmount(outputIngredient, "")) {
                amountLabel = formatMilliBuckets(producedAmount).replace(" ", "");
                exactAmountLabel = formatMilliBuckets(producedAmount);
            } else {
                amountLabel = formatCompactCount(crafts);
                exactAmountLabel = Component.translatable("gui.jeict.recipe_tree.amount_exact", crafts).getString();
            }
        }
        return new GraphNode(node.recipe().primaryOutputIngredient(), node.recipe().title().getString(), amountLabel,
                exactAmountLabel,
                node, node.recipe().subtitleIcon(), node.recipe().subtitle(), null, false, cycleWarning, children,
                computeNodeWidth(node.recipe().primaryOutputIngredient(), amountLabel,
                        node.recipe().subtitleIcon() != null, false,
                        node.parent() != null && (!children.isEmpty() || node.recipe().inputs().isEmpty())));
    }

    private List<LayerRow> buildMergedLayerRows(RecipeTreeNodeViewModel root, int crafts) {
        MergedBuildStats stats = new MergedBuildStats();
        List<LayerAccumulator> layers = new ArrayList<>();
        // Keep one queued traversal per canonical node/depth and add its craft counts together.
        // Compression chains and repeated 3x3 inputs can otherwise revisit equivalent branches many times.
        ArrayDeque<LayerTraversalKey> pending = new ArrayDeque<>();
        Map<LayerTraversalKey, Integer> queuedCrafts = new HashMap<>();
        Map<String, RecipeTreeNodeViewModel> canonicalNodes = new HashMap<>();
        enqueueLayerTraversal(pending, queuedCrafts, root, crafts, 0);
        while (!pending.isEmpty()) {
            LayerTraversalKey key = pending.removeFirst();
            int entryCrafts = queuedCrafts.remove(key);
            RecipeTreeNodeViewModel node = key.node();
            int depth = key.depth();
            stats.collectCalls++;
            layerAt(layers, depth).addNode(node, entryCrafts);
            stats.recipeNodeAdds++;

            RecipeTreeNodeViewModel firstChild = null;
            int firstChildAmount = 0;
            Map<RecipeTreeNodeViewModel, Integer> multipleChildren = null;

            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                RecipeTreeNodeViewModel child = input.child();
                if (child != null) {
                    child = canonicalNodeForMergedDisplay(child, canonicalNodes);
                }
                int amount = quantityPropagationCache.amount(input, entryCrafts);
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
                    enqueueLayerTraversal(pending, queuedCrafts, child,
                            ceilDiv(childEntry.getValue(), child.recipe().primaryOutputCount()), depth + 1);
                }
            } else if (firstChild != null) {
                enqueueLayerTraversal(pending, queuedCrafts, firstChild,
                        ceilDiv(firstChildAmount, firstChild.recipe().primaryOutputCount()), depth + 1);
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

    private void enqueueLayerTraversal(ArrayDeque<LayerTraversalKey> pending,
            Map<LayerTraversalKey, Integer> queuedCrafts, RecipeTreeNodeViewModel node, int crafts, int depth) {
        LayerTraversalKey key = new LayerTraversalKey(node, depth);
        Integer previous = queuedCrafts.putIfAbsent(key, crafts);
        if (previous == null) {
            pending.addLast(key);
        } else {
            queuedCrafts.put(key, safeAdd(previous, crafts));
        }
    }

    /**
     * Canonicalizes identical immediate recipe branches for the merged projection only. The mutable
     * source tree is left untouched, so recipe selection and memory semantics remain unchanged.
     */
    private RecipeTreeNodeViewModel canonicalNodeForMergedDisplay(RecipeTreeNodeViewModel node,
            Map<String, RecipeTreeNodeViewModel> canonicalNodes) {
        StringBuilder key = new StringBuilder(node.recipe().stableIdentity());
        key.append('|').append(node.recipe().primaryOutputAmount());
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            key.append('|').append(input.selectedAlternativeIndex()).append(':').append(input.amount());
            RecipeTreeNodeViewModel child = input.child();
            key.append(':').append(child == null ? leafSignatureOf(input) : child.recipe().stableIdentity());
        }
        return canonicalNodes.computeIfAbsent(key.toString(), ignored -> node);
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
            RequestedIngredient material = accumulator.toRequestedIngredient();
            rebuiltTopMaterials.add(material);
            unresolvedInputsBySignature.put(entry.getKey(), accumulator.slots());
            unresolvedInputsBySignature.put(signatureOf(material), accumulator.slots());
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
            result.add(new GenericTopMaterialRenderData(leaf, label, width, hasCycleWarning(leaf.members())));
        }
        return List.copyOf(result);
    }

    private void collectGenericTopMaterials(RecipeTreeNodeViewModel root, int rootCrafts,
            Map<String, ItemTopMaterialAccumulator> itemMaterials,
            Map<String, GenericTopMaterialAccumulator> genericMaterials) {
        // Top-material totals do not need one traversal per incoming parent link. Merge queued
        // demands by node identity before expanding the next level.
        ArrayDeque<RecipeTreeNodeViewModel> pending = new ArrayDeque<>();
        IdentityHashMap<RecipeTreeNodeViewModel, Integer> queuedCrafts = new IdentityHashMap<>();
        queuedCrafts.put(root, rootCrafts);
        pending.addLast(root);
        while (!pending.isEmpty()) {
            RecipeTreeNodeViewModel node = pending.removeFirst();
            int crafts = queuedCrafts.remove(node);
            RecipeTreeNodeViewModel firstChild = null;
            int firstChildAmount = 0;
            Map<RecipeTreeNodeViewModel, Integer> multipleChildren = null;
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                RecipeTreeNodeViewModel child = input.child();
                int amount = quantityPropagationCache.amount(input, crafts);
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
                    enqueueTopMaterialTraversal(pending, queuedCrafts, child,
                            ceilDiv(entry.getValue(), child.recipe().primaryOutputCount()));
                }
            } else if (firstChild != null) {
                enqueueTopMaterialTraversal(pending, queuedCrafts, firstChild,
                        ceilDiv(firstChildAmount, firstChild.recipe().primaryOutputCount()));
            }
        }
    }

    private void enqueueTopMaterialTraversal(ArrayDeque<RecipeTreeNodeViewModel> pending,
            IdentityHashMap<RecipeTreeNodeViewModel, Integer> queuedCrafts,
            RecipeTreeNodeViewModel node, int crafts) {
        Integer previous = queuedCrafts.putIfAbsent(node, crafts);
        if (previous == null) {
            pending.addLast(node);
        } else {
            queuedCrafts.put(node, safeAdd(previous, crafts));
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
                    inventoryAdjustedLabelColor(material, displayCount), label, width, stack, showsPatternHint,
                    hasCycleWarning(material)));
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
                    label, width, stack, false, false));
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

    private void fitFocusedTreeView() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) {
            host.fitFocusedTreeView();
            return;
        }
        RecipeTreeOverviewScreen target = focusedTree();
        WorkspaceTreePlacement placement = workspaceTreePlacements().stream()
                .filter(candidate -> candidate.screen() == target)
                .findFirst()
                .orElse(null);
        if (placement == null) {
            fitWorkspaceView();
            return;
        }
        TreeLogicalBounds bounds = placement.bounds();
        fitInitialView(bounds.minX() - 48.0D, bounds.minY() - 54.0D,
                bounds.maxX() + 48.0D, bounds.maxY() + 48.0D);
        initializedPan = true;
        initializedWorkspaceView = true;
        updateSelectionButtons();
    }

    private void fitWorkspaceView() {
        if (workspaceHost != null) {
            workspaceHost.fitWorkspaceView();
            return;
        }
        List<WorkspaceTreePlacement> placements = workspaceTreePlacements();
        if (placements.isEmpty()) {
            fitCurrentView();
            return;
        }
        double minX = placements.stream().mapToDouble(p -> p.bounds().minX()).min().orElse(0.0D) - 130.0D;
        double maxX = placements.stream().mapToDouble(p -> p.bounds().maxX()).max().orElse(1.0D) + 130.0D;
        double minY = placements.stream().mapToDouble(p -> p.bounds().minY()).min().orElse(0.0D) - 150.0D;
        double maxY = placements.stream().mapToDouble(p -> p.bounds().maxY()).max().orElse(1.0D) + 120.0D;
        fitInitialView(minX, minY, maxX, maxY);
        initializedPan = true;
        initializedWorkspaceView = true;
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

    private boolean isLayerCollapseButtonAt(LayerMaterialBounds bounds, double logicalMouseX, double logicalMouseY) {
        if (!bounds.material().collapsible()) return false;
        int buttonX = bounds.x() + bounds.width() - NODE_PART_PADDING - 10;
        int buttonY = bounds.y() + 9;
        return logicalMouseX >= buttonX && logicalMouseX <= buttonX + 10
                && logicalMouseY >= buttonY && logicalMouseY <= buttonY + 10;
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
            boolean cycleWarning, @Nullable MergedLeaf leafProjection) {
        private LayerMaterial {
            recipeTargets = recipeTargets == null ? List.of() : List.copyOf(recipeTargets);
            leafInputs = leafInputs == null ? List.of() : List.copyOf(leafInputs);
            leafParents = leafParents == null ? List.of() : List.copyOf(leafParents);
        }

        LayerMaterial withShowsPatternHint(boolean hint) {
            return hint == showsPatternHint
                    ? this
                    : new LayerMaterial(ingredient, label, amountLabel, width, hasAlternatives, totalAmount, recipeTargets,
                            leafInputs, leafParents, machineIcon, machineName, hint, cycleWarning, leafProjection);
        }

        private boolean hasUnresolvedLeaves() {
            return leafInputs.stream().anyMatch(input -> input.child() == null);
        }

        private boolean collapsible() {
            return recipeTargets.stream().anyMatch(node -> node.parent() != null);
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
            boolean showCollapseButton = recipeTargets.stream().anyMatch(node -> node.parent() != null);
            int slotWidth = computeNodeWidth(ingredient, amountLabel, machineIcon != null,
                    showAlternativesButton, showCollapseButton);
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
                    List.copyOf(recipeTargets), List.copyOf(leafInputs), List.copyOf(leafParents), machineIcon, machineName, hint, hasCycleWarning(leafInputs),
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
                showsPatternHint, hasCycleWarning(group), List.of(), computeNodeWidth(leaf.ingredient(), amountLabel, false,
                        leaf.representative().hasAlternativeChoices(), false)));
    }

    private int computeNodeWidth(@Nullable ITypedIngredient<?> ingredient, String amountLabel,
            boolean hasMachineIcon, boolean hasAlternativeButton, boolean hasCollapseButton) {
        int width = ingredientAreaWidth();
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
        if (isMekanismChemical(leaf.ingredient())) {
            return formatChemicalAmount(Math.max(1, leaf.totalAmount()));
        }
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
        if (isMekanismChemical(ingredient)) {
            return formatChemicalAmount(safe);
        }
        if (isMilliBucketAmount(ingredient, sampleText)) {
            return formatMilliBuckets(safe);
        }
        if (sampleText != null && !sampleText.isBlank() && !sampleText.startsWith("x")) {
            return sampleText;
        }
        return formatCompactCount(safe);
    }

    private static boolean isMilliBucketAmount(@Nullable ITypedIngredient<?> ingredient, @Nullable String amountText) {
        if (amountText != null && amountText.endsWith(" B")) {
            return true;
        }
        if (ingredient == null) {
            return false;
        }
        if (ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).filter(stack -> !stack.isEmpty()).isPresent()) {
            return true;
        }
        return false;
    }

    private static boolean isMekanismChemical(@Nullable ITypedIngredient<?> ingredient) {
        return ingredient != null
                && GenericIngredientUtil.tryGetMekanismChemicalAmount(ingredient.getIngredient()) > 0L;
    }

    private static String formatMilliBuckets(long amount) {
        String buckets = java.math.BigDecimal.valueOf(Math.max(1L, amount), 3)
                .stripTrailingZeros().toPlainString();
        return buckets + " B";
    }

    private static String formatPatternSlotAmount(PatternEncodingSlot slot) {
        ITypedIngredient<?> ingredient = slot.ingredient();
        Object raw = ingredient == null ? null : ingredient.getIngredient();
        if (raw instanceof FluidStack) {
            String buckets = java.math.BigDecimal.valueOf(Math.max(1L, slot.amount()), 3)
                    .stripTrailingZeros().toPlainString();
            return buckets.startsWith("0.") ? buckets.substring(1) : buckets;
        }
        if (GenericIngredientUtil.tryGetMekanismChemicalAmount(raw) > 0L) {
            return formatCompactChemicalAmount(slot.amount());
        }
        return formatCompactCount(slot.amount());
    }

    private static String formatChemicalAmount(long amount) {
        long safeAmount = Math.max(1L, amount);
        if (safeAmount < 1_000L) {
            return safeAmount + " mB";
        }
        return java.math.BigDecimal.valueOf(safeAmount, 3).stripTrailingZeros().toPlainString() + " B";
    }

    private static String formatCompactChemicalAmount(long amount) {
        return formatChemicalAmount(amount).replace(" ", "");
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
        if (clippedWorkspace && RecipeTreeTheme.isClassicStyle()) {
            graphics.drawManaged(() -> RecipeTreeTheme.drawBlueprintGrid(graphics,
                    canvasLeft() + 2, HEADER_HEIGHT + 2, canvasRight() - 2, this.height - footerHeight - 2,
                    panX, panY, zoom));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);
        renderAllWorkspaceTrees(graphics, theme);
        renderWorkspaceLinks(graphics, theme);
        graphics.pose().popPose();
        if (clippedWorkspace) {
            graphics.disableScissor();
            renderWorkspaceFrame(graphics, theme);
        }

        renderOverlayPanelBackdrops(graphics, theme);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderAe2WidgetChrome(graphics, mouseX, mouseY);
        renderDockPanels(graphics, theme, mouseX, mouseY);
        if (headerTitleRight > headerTitleLeft) {
            graphics.enableScissor(headerTitleLeft, 5, headerTitleRight, HEADER_HEIGHT - 2);
            RecipeTreeOverviewScreen focused = focusedTree();
            graphics.drawString(this.font, focused.context.root().recipe().title(), headerTitleLeft + 2, 7, theme.titleText(), false);
            graphics.drawString(this.font, focused.cachedRequiredPatternsTitleLine, headerTitleLeft + 2, 20, theme.metricText(), false);
            graphics.disableScissor();
        }
        String zoomLabel = Math.round(zoom * 100.0D) + "%";
        graphics.drawCenteredString(this.font, zoomLabel, this.width - 106, 14, theme.metricText());
        renderFooterStatus(graphics, theme);
        renderScreenNotice(graphics, theme);


        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        focusedTree().renderAlternativeSelection(graphics, mouseX, mouseY);
        graphics.pose().popPose();

        double logicalMouseX = (mouseX - panX) / zoom;
        double logicalMouseY = (mouseY - panY) / zoom;
        renderWorkspaceTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
        logRenderedTreeVisibility();
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

    private TreeLogicalBounds currentTreeLogicalBounds() {
        TreeLogicalBounds treeBounds;
        if (autoMergeSameMaterials) {
            double minX = 36.0D;
            treeBounds = new TreeLogicalBounds(minX, minX + Math.max(1, cachedMergedContentWidth),
                    42.0D + TOP_MATERIALS_OFFSET - 28.0D,
                    42.0D + TOP_MATERIALS_OFFSET + Math.max(NODE_HEIGHT, computeMergedLayerContentHeight()));
        } else {
            double minX = positionedNodes.stream().mapToDouble(PositionedNode::x).min().orElse(36.0D);
            double maxX = positionedNodes.stream().mapToDouble(node -> node.x() + node.graph().width())
                    .max().orElse(minX + NODE_MAIN_SIZE);
            double minY = positionedNodes.stream().mapToDouble(PositionedNode::y).min().orElse(142.0D);
            double maxY = positionedNodes.stream().mapToDouble(node -> node.y() + NODE_HEIGHT)
                    .max().orElse(minY + NODE_HEIGHT);
            treeBounds = new TreeLogicalBounds(minX, maxX, minY, maxY);
        }
        return includeTopMaterialRows(treeBounds);
    }

    private TreeLogicalBounds includeTopMaterialRows(TreeLogicalBounds treeBounds) {
        RootBatchAnchor root = findRootBatchAnchor();
        if (root == null || (topMaterialRenderData.isEmpty() && genericTopMaterialRenderData.isEmpty())) {
            return treeBounds;
        }
        int gap = 4;
        int requiredWidth = totalMaterialRowWidth(topMaterialRenderData, gap)
                + (topMaterialRenderData.isEmpty() || genericTopMaterialRenderData.isEmpty() ? 0 : gap)
                + totalGenericMaterialRowWidth(gap);
        int surplusWidth = totalMaterialRowWidth(surplusMaterialRenderData, gap);
        int rootCenterX = (root.left() + root.right()) / 2;
        int requiredLeft = rootCenterX - requiredWidth / 2;
        int surplusLeft = rootCenterX - surplusWidth / 2;
        int materialLeft = surplusWidth > 0 ? Math.min(requiredLeft, surplusLeft) : requiredLeft;
        int pinRight = requiredLeft + requiredWidth + 20;
        int materialRight = surplusWidth > 0
                ? Math.max(pinRight, surplusLeft + surplusWidth) : pinRight;
        int panelY = root.y() - NODE_HEIGHT * 2 - 28;
        int materialBottom = surplusWidth > 0 ? panelY + NODE_HEIGHT * 2 + 20 : panelY + NODE_HEIGHT + 4;
        return new TreeLogicalBounds(
                Math.min(treeBounds.minX(), materialLeft - 4.0D),
                Math.max(treeBounds.maxX(), materialRight),
                Math.min(treeBounds.minY(), panelY - 14.0D),
                Math.max(treeBounds.maxY(), materialBottom));
    }

    private int totalGenericMaterialRowWidth(int gap) {
        if (genericTopMaterialRenderData.isEmpty()) return 0;
        int width = 0;
        for (GenericTopMaterialRenderData data : genericTopMaterialRenderData) {
            width += data.width() + gap;
        }
        return Math.max(0, width - gap);
    }

    private RecipeTreeOverviewScreen workspaceHostScreen() {
        return workspaceHost == null ? this : workspaceHost;
    }

    private RecipeTreeOverviewScreen focusedTree() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        return host.focusedWorkspaceTree == null ? host : host.focusedWorkspaceTree;
    }

    private void syncWorkspaceTreeScreens() {
        if (workspaceHost != null) return;
        for (RecipeTreeRootContext treeContext : workspace.trees().values()) {
            if (treeContext == context || workspaceTreeScreens.containsKey(treeContext)) continue;
            RecipeTreeOverviewScreen embedded = new RecipeTreeOverviewScreen(treeContext, this, workspace);
            embedded.workspaceHost = this;
            workspaceTreeScreens.put(treeContext, embedded);
            if (this.minecraft != null && this.width > 0 && this.height > 0) {
                embedded.init(this.minecraft, this.width, this.height);
            }
        }
        RecipeTreeOverviewScreen focused = focusedWorkspaceTree == null ? this : focusedWorkspaceTree;
        workspace.activate(focused.context);
    }

    /** Called after JEI adds a root; keeps this screen and attaches another independently editable tree. */
    public void addWorkspaceContext(RecipeTreeRootContext addedContext) {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) {
            host.addWorkspaceContext(addedContext);
            return;
        }
        if (addedContext == null || addedContext == context || workspaceTreeScreens.containsKey(addedContext)) return;
        RecipeTreeOverviewScreen embedded = new RecipeTreeOverviewScreen(addedContext, this, workspace);
        embedded.workspaceHost = this;
        workspaceTreeScreens.put(addedContext, embedded);
        if (this.minecraft != null && this.width > 0 && this.height > 0) {
            embedded.init(this.minecraft, this.width, this.height);
        }
        focusedWorkspaceTree = embedded;
        workspace.activate(addedContext);
        RecipeTreeOpenHelper.rememberWorkspace(workspace);
        fitWorkspaceView();
    }

    private List<WorkspaceTreePlacement> workspaceTreePlacements() {
        syncWorkspaceTreeScreens();
        Map<GridPosition, RecipeTreeRootContext> trees = workspace.trees();
        Map<Integer, Double> columnWidths = new HashMap<>();
        Map<Integer, Double> rowHeights = new HashMap<>();
        for (Map.Entry<GridPosition, RecipeTreeRootContext> entry : trees.entrySet()) {
            RecipeTreeOverviewScreen screen = screenFor(entry.getValue());
            TreeLogicalBounds bounds = screen.currentTreeLogicalBounds();
            columnWidths.merge(entry.getKey().x(), bounds.width(), Math::max);
            rowHeights.merge(entry.getKey().y(), bounds.height(), Math::max);
        }
        List<Integer> columns = columnWidths.keySet().stream().sorted().toList();
        List<Integer> rows = rowHeights.keySet().stream().sorted().toList();
        Map<Integer, Double> columnCenters = centeredAxisPositions(columns, columnWidths, 260.0D);
        Map<Integer, Double> rowCenters = centeredAxisPositions(rows, rowHeights, 220.0D);
        List<WorkspaceTreePlacement> result = new ArrayList<>();
        for (Map.Entry<GridPosition, RecipeTreeRootContext> entry : trees.entrySet()) {
            RecipeTreeOverviewScreen screen = screenFor(entry.getValue());
            TreeLogicalBounds bounds = screen.currentTreeLogicalBounds();
            double offsetX = columnCenters.get(entry.getKey().x()) - bounds.centerX();
            double offsetY = rowCenters.get(entry.getKey().y()) - bounds.centerY();
            result.add(new WorkspaceTreePlacement(entry.getKey(), screen, offsetX, offsetY,
                    bounds.translated(offsetX, offsetY)));
        }
        return result;
    }

    private static Map<Integer, Double> centeredAxisPositions(List<Integer> coordinates,
            Map<Integer, Double> sizes, double gap) {
        Map<Integer, Double> result = new HashMap<>();
        if (coordinates.isEmpty()) return result;
        int zeroIndex = coordinates.indexOf(0);
        if (zeroIndex < 0) zeroIndex = 0;
        result.put(coordinates.get(zeroIndex), 0.0D);
        for (int i = zeroIndex + 1; i < coordinates.size(); i++) {
            int previous = coordinates.get(i - 1);
            int current = coordinates.get(i);
            result.put(current, result.get(previous) + sizes.get(previous) * 0.5D + gap + sizes.get(current) * 0.5D);
        }
        for (int i = zeroIndex - 1; i >= 0; i--) {
            int next = coordinates.get(i + 1);
            int current = coordinates.get(i);
            result.put(current, result.get(next) - sizes.get(next) * 0.5D - gap - sizes.get(current) * 0.5D);
        }
        return result;
    }

    private RecipeTreeOverviewScreen screenFor(RecipeTreeRootContext treeContext) {
        return treeContext == context ? this : workspaceTreeScreens.get(treeContext);
    }

    private void renderAllWorkspaceTrees(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        double hostMinX = this.visibleLogicalMinX;
        double hostMaxX = this.visibleLogicalMaxX;
        double hostMinY = this.visibleLogicalMinY;
        double hostMaxY = this.visibleLogicalMaxY;
        for (WorkspaceTreePlacement placement : workspaceTreePlacements()) {
            RecipeTreeOverviewScreen tree = placement.screen();
            syncEmbeddedViewport(tree, placement, hostMinX, hostMaxX, hostMinY, hostMaxY);
            if (tree == this) {
                tree.visibleLogicalMinX = hostMinX - placement.offsetX();
                tree.visibleLogicalMaxX = hostMaxX - placement.offsetX();
                tree.visibleLogicalMinY = hostMinY - placement.offsetY();
                tree.visibleLogicalMaxY = hostMaxY - placement.offsetY();
            }
            if (tree != this && this.searchBox != null && tree.searchBox != null
                    && !tree.searchBox.getValue().equals(this.searchBox.getValue())) {
                tree.searchBox.setValue(this.searchBox.getValue());
            }
            graphics.pose().pushPose();
            graphics.pose().translate(placement.offsetX(), placement.offsetY(), 0.0D);
            tree.renderSingleTreeCanvas(graphics, theme);
            if (workspace.size() > 1) {
                tree.renderWorkspaceTreeTitle(graphics, theme, tree == focusedTree());
            }
            graphics.pose().popPose();
            if (tree == this) {
                this.visibleLogicalMinX = hostMinX;
                this.visibleLogicalMaxX = hostMaxX;
                this.visibleLogicalMinY = hostMinY;
                this.visibleLogicalMaxY = hostMaxY;
            }
        }
    }

    /** Keeps every embedded tree's culling and screen-space helpers on the host viewport. */
    private void syncEmbeddedViewport(RecipeTreeOverviewScreen tree, WorkspaceTreePlacement placement,
            double hostMinX, double hostMaxX, double hostMinY, double hostMaxY) {
        if (tree == this) return;
        tree.zoom = this.zoom;
        tree.panX = this.panX + placement.offsetX() * this.zoom;
        tree.panY = this.panY + placement.offsetY() * this.zoom;
        tree.visibleLogicalMinX = hostMinX - placement.offsetX();
        tree.visibleLogicalMaxX = hostMaxX - placement.offsetX();
        tree.visibleLogicalMinY = hostMinY - placement.offsetY();
        tree.visibleLogicalMaxY = hostMaxY - placement.offsetY();
    }

    private void renderWorkspaceTreeTitle(GuiGraphics graphics, RecipeTreeTheme.Palette theme, boolean focused) {
        RootBatchAnchor root = findRootBatchAnchor();
        if (root == null) return;
        int gap = 12;
        int maxWidth = Math.max(72, Math.min(220, root.left() - (int) currentTreeLogicalBounds().minX() + 180));
        String title = this.font.plainSubstrByWidth(context.title().getString(), maxWidth);
        int titleWidth = this.font.width(title);
        int titleX = root.left() - gap - titleWidth;
        int titleY = root.y() + Math.max(0, (NODE_HEIGHT - this.font.lineHeight) / 2);
        graphics.drawString(this.font, title, titleX, titleY,
                focused ? theme.focusHighlight() : theme.titleText(), false);
    }

    private void renderSingleTreeCanvas(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
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
    }

    private void renderWorkspaceLinks(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        workspaceLinkBounds.clear();
        for (WorkspaceTreePlacement placement : workspaceTreePlacements()) {
            TreeLogicalBounds bounds = placement.bounds();
            double centerX = bounds.centerX();
            double centerY = bounds.centerY();
            for (Direction direction : Direction.values()) {
                if (workspace.neighbor(placement.position(), direction) != null) continue;
                Component label = Component.translatable(
                        "gui.jeict.recipe_tree.workspace_add_" + direction.name().toLowerCase());
                int width = Math.min(160, Math.max(70, this.font.width(label) + 12));
                String visibleText = this.font.plainSubstrByWidth(label.getString(), Math.max(1, width - 12));
                int height = 16;
                double x;
                double y;
                switch (direction) {
                    case UP -> { x = centerX - width * 0.5D; y = bounds.minY() - 125.0D; }
                    case DOWN -> { x = centerX - width * 0.5D; y = bounds.maxY() + 95.0D; }
                    case LEFT -> { x = bounds.minX() - width - 100.0D; y = centerY - height * 0.5D; }
                    case RIGHT -> { x = bounds.maxX() + 100.0D; y = centerY - height * 0.5D; }
                    default -> throw new IllegalStateException("Unexpected direction " + direction);
                }
                workspaceLinkBounds.add(new WorkspaceLinkBounds(placement.position(), direction, x, y, width, height));
                graphics.drawCenteredString(this.font, Component.literal(visibleText),
                        (int) x + width / 2, (int) y + 4, theme.linkText());
            }
        }
    }

    private boolean handleWorkspaceLinkClick(double logicalMouseX, double logicalMouseY) {
        for (WorkspaceLinkBounds bounds : workspaceLinkBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY)) continue;
            RecipeTreeOpenHelper.beginWorkspaceAdd(workspace, bounds.origin(), bounds.direction(), this);
            return true;
        }
        return false;
    }

    private boolean routeWorkspaceTreeClick(double mouseX, double mouseY, int button) {
        List<WorkspaceTreePlacement> placements = workspaceTreePlacements();
        double workspaceX = (mouseX - panX) / zoom;
        double workspaceY = (mouseY - panY) / zoom;
        for (int i = placements.size() - 1; i >= 0; i--) {
            WorkspaceTreePlacement placement = placements.get(i);
            RecipeTreeOverviewScreen tree = placement.screen();
            double localLogicalX = workspaceX - placement.offsetX();
            double localLogicalY = workspaceY - placement.offsetY();
            TreeLogicalBounds hitBounds = tree.currentTreeLogicalBounds().expanded(48.0D, 130.0D);
            if (!hitBounds.contains(localLogicalX, localLogicalY)) continue;
            syncEmbeddedViewport(tree, placement, visibleLogicalMinX, visibleLogicalMaxX,
                    visibleLogicalMinY, visibleLogicalMaxY);
            focusedWorkspaceTree = tree;
            workspace.activate(tree.context);
            syncToggleExistingPatternButton();
            updateSelectionButtons();
            return tree.handleTreeCanvasClick(localLogicalX, localLogicalY, button);
        }
        return false;
    }

    private boolean handleTreeCanvasClick(double logicalMouseX, double logicalMouseY, int button) {
        if (button == 0 && topMaterialsPinButtonBounds != null
                && topMaterialsPinButtonBounds.contains(logicalMouseX, logicalMouseY)) {
            FloatingMaterialOverlayState.set(createFloatingMaterialSnapshot());
            onClose();
            return true;
        }
        if (button == 0) {
            for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                if (bounds.contains(logicalMouseX, logicalMouseY)) {
                    openAlternativeSelection(bounds);
                    return true;
                }
            }
        }
        GenericTopMaterialBounds genericTarget = findGenericTopMaterialAt(logicalMouseX, logicalMouseY);
        if (genericTarget != null) {
            if (button == 0) {
                openSelectionWithJei(genericTarget.data().leaf());
            } else {
                jumpToMaterialSignature(signatureOf(genericTarget.data().leaf().representative()));
            }
            return true;
        }
        TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
        if (materialTarget != null) {
            if (button == 0) {
                MergedLeaf leaf = findLeafForMaterial(materialTarget.material());
                if (leaf != null) openSelectionWithJei(leaf);
            } else {
                jumpToMaterial(materialTarget.material());
            }
            return true;
        }
        if (autoMergeSameMaterials) {
            LayerMaterialBounds clicked = findLayerAt(logicalMouseX, logicalMouseY);
            if (clicked != null) {
                if (button == 0 && isLayerCollapseButtonAt(clicked, logicalMouseX, logicalMouseY)) {
                    collapseLayerMaterial(clicked.material());
                    closeSelection();
                    rebuildLayout();
                    return true;
                }
                if (button == 0) {
                    return handleMergedLayerMaterialClick(clicked.material());
                }
                selectedLayerMaterial = clicked.material();
                selectedNode = null;
                return true;
            }
            if (button == 0) selectedLayerMaterial = null;
            return false;
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
        if (clicked == null) {
            if (button == 0) selectedNode = null;
            return false;
        }
        if (button == 1) {
            selectedNode = clicked;
            selectedLayerMaterial = null;
            return true;
        }
        if (clicked.graph().recipeNode() != null) {
            openSelectionWithJei(clicked.graph().recipeNode());
            return true;
        }
        if (clicked.graph().mergedLeaf() != null) {
            openSelectionWithJei(clicked.graph().mergedLeaf());
            return true;
        }
        return false;
    }

    private void logRenderedTreeVisibility() {
        int structureCount = autoMergeSameMaterials ? mergedLayerRows.size() : positionedNodes.size();
        int visibleCount = autoMergeSameMaterials ? lastRenderedLayerMaterialCount : lastRenderedNodeCount;
        if (visibleCount > 0) {
            if (diagnosticEmptyRenderFrames > 0) {
                LOGGER.info("[JEICT-TREE] tree rendering recovered after {} empty frames; visible={} structure={} merge={} pan=({}, {}) zoom={}",
                        diagnosticEmptyRenderFrames, visibleCount, structureCount, autoMergeSameMaterials,
                        Math.round(panX * 100.0D) / 100.0D, Math.round(panY * 100.0D) / 100.0D,
                        Math.round(zoom * 1000.0D) / 1000.0D);
            }
            diagnosticRenderedTreeOnce = true;
            diagnosticEmptyRenderFrames = 0;
            return;
        }
        if (structureCount <= 0) return;
        diagnosticEmptyRenderFrames++;
        if (diagnosticEmptyRenderFrames == 2 || diagnosticEmptyRenderFrames == 20) {
            LOGGER.warn("[JEICT-TREE] tree structure exists but nothing is visible for {} frames; renderedBefore={} merge={} nodes={} edges={} rows={} visibleNodes={} visibleRows={} visibleMaterials={} pan=({}, {}) zoom={} logicalBounds=({}, {})-({}, {}) canvas={}..{} screen={}x{} planningBusy={} inventoryVersion={}",
                    diagnosticEmptyRenderFrames, diagnosticRenderedTreeOnce, autoMergeSameMaterials,
                    positionedNodes.size(), edges.size(), mergedLayerRows.size(), lastRenderedNodeCount,
                    lastRenderedLayerCount, lastRenderedLayerMaterialCount,
                    Math.round(panX * 100.0D) / 100.0D, Math.round(panY * 100.0D) / 100.0D,
                    Math.round(zoom * 1000.0D) / 1000.0D,
                    Math.round(visibleLogicalMinX * 100.0D) / 100.0D,
                    Math.round(visibleLogicalMinY * 100.0D) / 100.0D,
                    Math.round(visibleLogicalMaxX * 100.0D) / 100.0D,
                    Math.round(visibleLogicalMaxY * 100.0D) / 100.0D,
                    canvasLeft(), canvasRight(), width, height, planningBusy, planningInventoryVersion);
        }
    }

    /** MEST-style floating AE2 terminal shell with a restrained content well. */
    private int canvasLeft() {
        return settingsOpen ? SETTINGS_WIDTH + 8 : SETTINGS_COLLAPSED_WIDTH + 6;
    }

    private int canvasRight() {
        boolean inspectorVisible = workspaceHost == null
                ? focusedTree().hasInspectorSelection() : hasInspectorSelection();
        return inspectorVisible && this.width >= 520 ? this.width - INSPECTOR_WIDTH - 8 : this.width - 6;
    }

    private boolean hasInspectorSelection() {
        return selectedNode != null || selectedLayerMaterial != null;
    }

    private void renderOverlayPanelBackdrops(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        if (focusedTree().hasInspectorSelection() && this.width >= 520) {
            RecipeTreeTheme.drawFramedPanel(graphics, canvasRight() + 6, HEADER_HEIGHT + 4, this.width - 6,
                    this.height - currentFooterHeight() - 4);
        }
        RecipeTreeTheme.drawRaisedPanel(graphics, 4, HEADER_HEIGHT + 2, canvasLeft() - 2,
                this.height - currentFooterHeight() - 2);
    }

    private void renderDockPanels(GuiGraphics graphics, RecipeTreeTheme.Palette theme, int mouseX, int mouseY) {
        RecipeTreeOverviewScreen editor = focusedTree();
        editor.renderInspectorPanel(graphics, theme);
        if (editor != this && editor.patternAmountEditor != null && editor.patternAmountEditor.visible) {
            editor.patternAmountEditor.render(graphics, mouseX, mouseY, 0.0F);
        }
    }

    private void renderInspectorPanel(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        inspectorPatternSlotBounds.clear();
        inspectorPatternControlBounds.clear();
        if (!hasInspectorSelection() || this.width < 520) {
            inspectorScroll = 0;
            inspectorMaxScroll = 0;
            inspectorViewportTop = 0;
            inspectorViewportBottom = 0;
            lastInspectorScrollIdentity = null;
            return;
        }

        int left = canvasRight() + 16;
        int right = this.width - 16;
        int top = HEADER_HEIGHT + 14;
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.inspector_title"), left, top,
                theme.titleText(), false);

        String selectionIdentity = inspectorSelectionIdentity();
        if (!selectionIdentity.equals(lastInspectorScrollIdentity)) {
            lastInspectorScrollIdentity = selectionIdentity;
            inspectorScroll = 0;
            inspectorMaxScroll = 0;
            closePatternAmountEditor(false);
        }

        inspectorViewportTop = top + 15;
        inspectorViewportBottom = Math.max(inspectorViewportTop + 1,
                this.height - currentFooterHeight() - 10);
        int contentTop = inspectorViewportTop - inspectorScroll;

        graphics.enableScissor(canvasRight() + 8, inspectorViewportTop, this.width - 8, inspectorViewportBottom);
        int contentBottom = renderInspectorContents(graphics, theme, left, right, contentTop);
        graphics.disableScissor();

        int contentHeight = Math.max(0, contentBottom - contentTop);
        int viewportHeight = Math.max(1, inspectorViewportBottom - inspectorViewportTop);
        inspectorMaxScroll = Math.max(0, contentHeight - viewportHeight);
        if (inspectorScroll > inspectorMaxScroll) {
            inspectorScroll = inspectorMaxScroll;
        }
        renderInspectorScrollbar(graphics, theme, contentHeight, viewportHeight);
    }

    private int renderInspectorContents(GuiGraphics graphics, RecipeTreeTheme.Palette theme,
            int left, int right, int contentTop) {
        String label;
        String amount;
        @Nullable ITypedIngredient<?> ingredient;
        @Nullable Component machineName;
        boolean recipeNode;
        boolean patternHint;
        @Nullable RecipeTreeRecipeViewModel previewRecipe = selectedInspectorRecipe();
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

        RecipeTreeTheme.drawSlot(graphics, left, contentTop);
        renderIngredientAt(graphics, ingredient, left + 1, contentTop + 1);
        graphics.drawString(this.font, trimToWidth(Component.literal(label), Math.max(20, right - left - 28)),
                left + 26, contentTop + 3, theme.titleText(), false);
        int textY = contentTop + 30;
        if (amount != null && !amount.isBlank()) {
            graphics.drawString(this.font, Component.literal(amount), left, textY, theme.metricText(), false);
            textY += 15;
        }
        graphics.drawString(this.font, Component.translatable(recipeNode
                ? "gui.jeict.recipe_tree.inspector_recipe"
                : "gui.jeict.recipe_tree.inspector_material"), left, textY, theme.mutedText(), false);
        textY += 15;
        if (recipeNode && previewRecipe != null) {
            textY = renderJeiRecipePreview(graphics, previewRecipe, left, right, textY, theme);
        }
        if (machineName != null) {
            graphics.drawString(this.font, trimToWidth(machineName, right - left), left, textY, theme.hintText(), false);
            textY += 15;
        }
        if (patternHint) {
            graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.pattern_exists"),
                    left, textY, theme.success(), false);
            textY += 15;
        }

        CraftingTreeBackend patternBackend = CraftingTreeBackends.get();
        if (AE2_LOADED && patternBackend != null && patternBackend.supportsEditablePatternDrafts()) {
            graphics.fill(left, textY + 3, right, textY + 4, theme.gridMajorLine());
            textY += 11;
            PatternEncodingDraft draft = previewRecipe == null ? null : patternDraftFor(previewRecipe);
            Component heading = draft != null && draft.isDirty()
                    ? Component.translatable("gui.jeict.recipe_tree.pattern_editor_dirty").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.jeict.recipe_tree.pattern_editor");
            graphics.drawString(this.font, heading, left, textY, theme.titleText(), false);
            textY += 14;
            if (draft == null) {
                graphics.drawString(this.font,
                        trimToWidth(Component.translatable("gui.jeict.recipe_tree.pattern_preview_empty"), right - left),
                        left, textY, theme.hintText(), false);
                textY += 18;
            } else {
                if (!draft.sourceRecipeIdentity().equals(lastInspectorDraftIdentity)) {
                    lastInspectorDraftIdentity = draft.sourceRecipeIdentity();
                    patternDraftScroll = 0;
                    closePatternAmountEditor(false);
                }
                int panelX = left;
                int panelY = textY;
                renderAe2PatternDraft(graphics, draft, panelX, panelY, theme);
                patternAmountEditorX = Math.max(8, Math.min(this.width - 96, panelX));
                patternAmountEditorY = panelY + AE2_PATTERN_PANEL_HEIGHT + 5;
                if (patternAmountEditor != null && patternAmountEditor.visible) {
                    patternAmountEditor.setX(patternAmountEditorX);
                    patternAmountEditor.setY(patternAmountEditorY);
                    textY = patternAmountEditorY + patternAmountEditor.getHeight() + 4;
                } else {
                    textY = panelY + AE2_PATTERN_PANEL_HEIGHT + 18;
                }
                Component state = patternDraftValidationState(draft);
                graphics.drawString(this.font, trimToWidth(state, right - left), left, textY,
                        isPatternDraftValid(draft) ? (draft.isDirty() ? theme.partial() : theme.success()) : theme.danger(), false);
                textY += 13;
            }
        }
        graphics.fill(left, textY, right, textY + 1, theme.gridMajorLine());
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.inspector_action_hint"),
                left, textY + 8, theme.hintText(), false);
        return textY + 20;
    }

    private void renderInspectorScrollbar(GuiGraphics graphics, RecipeTreeTheme.Palette theme,
            int contentHeight, int viewportHeight) {
        if (inspectorMaxScroll <= 0 || contentHeight <= viewportHeight) {
            inspectorScrollbarTrackHeight = 0;
            inspectorScrollbarThumbHeight = 0;
            draggingInspectorScrollbar = false;
            return;
        }
        int trackX = this.width - 11;
        int trackTop = inspectorViewportTop + 1;
        int trackBottom = inspectorViewportBottom - 1;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.min(trackHeight,
                Math.max(18, Math.round(trackHeight * (viewportHeight / (float) contentHeight))));
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + Math.round(thumbTravel * (inspectorScroll / (float) inspectorMaxScroll));
        inspectorScrollbarTrackTop = trackTop;
        inspectorScrollbarTrackHeight = trackHeight;
        inspectorScrollbarThumbTop = thumbY;
        inspectorScrollbarThumbHeight = thumbHeight;
        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, theme.scrollbarTrack());
        graphics.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbHeight, theme.scrollbarThumb());
    }

    private String inspectorSelectionIdentity() {
        RecipeTreeRecipeViewModel recipe = selectedInspectorRecipe();
        if (recipe != null) {
            return "recipe:" + sourceRecipeForPatternDraft(recipe).stableIdentity();
        }
        if (selectedLayerMaterial != null) {
            ITypedIngredient<?> ingredient = selectedLayerMaterial.ingredient();
            return "material:" + (ingredient == null ? selectedLayerMaterial.label() : signatureOf(ingredient));
        }
        if (selectedNode != null && selectedNode.graph().ingredient() != null) {
            return "node:" + signatureOf(selectedNode.graph().ingredient());
        }
        return "selection:none";
    }

    private boolean isInsideInspectorViewport(double mouseX, double mouseY) {
        return hasInspectorSelection() && this.width >= 520
                && mouseX >= canvasRight() + 8 && mouseX < this.width - 8
                && mouseY >= inspectorViewportTop && mouseY < inspectorViewportBottom;
    }

    private int renderJeiRecipePreview(GuiGraphics graphics, RecipeTreeRecipeViewModel recipe,
            int left, int right, int textY, RecipeTreeTheme.Palette theme) {
        graphics.drawString(this.font, Component.translatable("gui.jeict.recipe_tree.jei_preview"),
                left, textY, theme.hintText(), false);
        textY += 12;

        RecipeTreeRecipeViewModel sourceRecipe = sourceRecipeForPatternDraft(recipe);
        String cacheKey = sourceRecipe.stableIdentity();
        Optional<IRecipeLayoutDrawable<?>> cached = jeiRecipePreviewCache.computeIfAbsent(cacheKey,
                ignored -> RecipeTreeJeiLookup.createRecipePreview(sourceRecipe));
        if (cached.isEmpty()) {
            graphics.drawString(this.font,
                    trimToWidth(Component.translatable("gui.jeict.recipe_tree.jei_preview_unavailable"), right - left),
                    left, textY + 3, theme.mutedText(), false);
            return textY + 18;
        }

        IRecipeLayoutDrawable<?> layout = cached.get();
        layout.setPosition(0, 0);
        var rect = layout.getRect();
        int sourceWidth = Math.max(1, rect.getWidth());
        int sourceHeight = Math.max(1, rect.getHeight());
        int availableWidth = Math.max(24, right - left);
        float scale = Math.min(1.0F, availableWidth / (float) sourceWidth);
        int drawnWidth = Math.max(1, Math.round(sourceWidth * scale));
        int drawnHeight = Math.max(1, Math.round(sourceHeight * scale));
        int drawX = left;
        int drawY = textY;

        // JEI's drawable already contains the recipe frame. Adding another panel here creates a double border.
        graphics.pose().pushPose();
        graphics.pose().translate(drawX - rect.getX() * scale, drawY - rect.getY() * scale, 20.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        layout.drawRecipe(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE);
        graphics.pose().popPose();
        return textY + drawnHeight + 5;
    }

    private @Nullable RecipeTreeNodeViewModel selectedInspectorRecipeNode() {
        if (selectedNode != null && selectedNode.graph().recipeNode() != null) {
            return selectedNode.graph().recipeNode();
        }
        if (selectedLayerMaterial != null && !selectedLayerMaterial.recipeTargets().isEmpty()) {
            return selectedLayerMaterial.recipeTargets().getFirst();
        }
        return null;
    }

    private @Nullable RecipeTreeRecipeViewModel selectedInspectorRecipe() {
        RecipeTreeNodeViewModel node = selectedInspectorRecipeNode();
        return node == null ? null : node.recipe();
    }

    private void restoreInspectorSelection(@Nullable RecipeTreeNodeViewModel target) {
        if (target == null) return;
        if (autoMergeSameMaterials) {
            for (LayerRow row : mergedLayerRows) {
                for (LayerMaterial material : row.materials()) {
                    for (RecipeTreeNodeViewModel recipeTarget : material.recipeTargets()) {
                        if (recipeTarget == target) {
                            selectedLayerMaterial = material;
                            selectedNode = null;
                            return;
                        }
                    }
                }
            }
            return;
        }
        for (PositionedNode positioned : positionedNodes) {
            if (positioned.graph().recipeNode() == target) {
                selectedNode = positioned;
                selectedLayerMaterial = null;
                return;
            }
        }
    }

    private PatternEncodingDraft patternDraftFor(RecipeTreeRecipeViewModel recipe) {
        RecipeTreeRecipeViewModel sourceRecipe = sourceRecipeForPatternDraft(recipe);
        String draftKey = sourceRecipe.stableIdentity();
        return patternDrafts.computeIfAbsent(draftKey, ignored -> {
            CraftingTreeBackend backend = CraftingTreeBackends.get();
            PatternEncodingMode mode = backend == null ? PatternEncodingMode.PROCESSING : backend.patternMode(sourceRecipe);
            boolean itemSubstitution = backend != null && backend.itemSubstituteOn();
            boolean fluidSubstitution = backend != null && backend.fluidSubstituteOn();
            patternDraftSourceRecipes.putIfAbsent(draftKey, sourceRecipe);
            return PatternEncodingDraft.fromRecipe(sourceRecipe, mode, itemSubstitution, fluidSubstitution);
        });
    }

    private RecipeTreeRecipeViewModel sourceRecipeForPatternDraft(RecipeTreeRecipeViewModel recipe) {
        RecipeTreeRecipeViewModel exact = patternDraftSourceRecipes.get(recipe.stableIdentity());
        if (exact != null) return exact;
        for (RecipeTreeRecipeViewModel source : patternDraftSourceRecipes.values()) {
            if (source.sameRecipeAs(recipe)) return source;
        }
        return recipe;
    }

    private void resetPatternDraft(RecipeTreeRecipeViewModel recipe) {
        RecipeTreeRecipeViewModel sourceRecipe = sourceRecipeForPatternDraft(recipe);
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        PatternEncodingMode mode = backend == null ? PatternEncodingMode.PROCESSING : backend.patternMode(sourceRecipe);
        PatternEncodingDraft resetDraft = PatternEncodingDraft.fromRecipe(sourceRecipe, mode,
                backend != null && backend.itemSubstituteOn(), backend != null && backend.fluidSubstituteOn());
        patternDrafts.put(resetDraft.sourceRecipeIdentity(), resetDraft);
        modifiedPatternRecipeKeys.remove(patternRecipeKey(sourceRecipe));
        existingPatternRecipeCache.remove(recipe);
        syncPatternDraftToRecipeTree(sourceRecipe, resetDraft);
        patternDraftScroll = 0;
        closePatternAmountEditor(false);
    }

    private void renderAe2PatternDraft(GuiGraphics graphics, PatternEncodingDraft draft, int x, int y,
            RecipeTreeTheme.Palette theme) {
        switch (draft.mode()) {
            case CRAFTING -> {
                graphics.blit(AE2_PATTERN_MODES_TEXTURE, x, y, AE2_PATTERN_PANEL_SRC_X,
                        AE2_CRAFTING_PANEL_SRC_Y, AE2_PATTERN_PANEL_WIDTH, AE2_PATTERN_PANEL_HEIGHT, 256, 256);
                for (int slot = 0; slot < 9; slot++) {
                    renderPatternDraftSlot(graphics, draft.input(slot), x + 7 + (slot % 3) * 18,
                            y + 7 + (slot / 3) * 18, true, slot);
                }
                renderPatternDraftSlot(graphics, draft.output(0), x + 98, y + 25, false, 0);
                // AE2 crafting mode: clear, item substitutions, fluid substitutions.
                renderPatternSmallControl(graphics, x + 62, y + 7, 224, 200, PatternControl.CLEAR);
                renderPatternSmallControl(graphics, x + 72, y + 7,
                        draft.substituteItems() ? 224 : 232, 208, PatternControl.ITEM_SUBSTITUTION);
                renderPatternSmallControl(graphics, x + 82, y + 7,
                        draft.substituteFluids() ? 224 : 232, 216, PatternControl.FLUID_SUBSTITUTION);
            }
            case PROCESSING -> {
                graphics.blit(AE2_PATTERN_MODES_TEXTURE, x, y, AE2_PATTERN_PANEL_SRC_X,
                        AE2_PROCESSING_PANEL_SRC_Y, AE2_PATTERN_PANEL_WIDTH, AE2_PATTERN_PANEL_HEIGHT, 256, 256);
                for (int visible = 0; visible < PatternEncodingDraft.VISIBLE_INPUTS; visible++) {
                    int slotIndex = patternDraftScroll * 3 + visible;
                    renderPatternDraftSlot(graphics, draft.input(slotIndex), x + 16 + (visible % 3) * 18,
                            y + 7 + (visible / 3) * 18, true, slotIndex);
                }
                for (int visible = 0; visible < PatternEncodingDraft.VISIBLE_OUTPUTS; visible++) {
                    int slotIndex = patternDraftScroll + visible;
                    renderPatternDraftSlot(graphics, draft.output(slotIndex), x + 101, y + 7 + visible * 18,
                            false, slotIndex);
                }
                // AE2 processing mode intentionally has no substitution buttons.
                renderPatternSmallControl(graphics, x + 71, y + 7, 224, 200, PatternControl.CLEAR);
                renderPatternSmallControl(graphics, x + 90, y + 7, 232, 200, PatternControl.CYCLE_OUTPUT);
                renderProcessingPatternScrollbar(graphics, draft, x, y);
            }
            case SMITHING_TABLE -> {
                graphics.blit(AE2_PATTERN_MODES_TEXTURE, x, y, AE2_SMITHING_PANEL_SRC_X,
                        AE2_SMITHING_PANEL_SRC_Y, AE2_PATTERN_PANEL_WIDTH, AE2_PATTERN_PANEL_HEIGHT, 256, 256);
                for (int slot = 0; slot < 3; slot++) {
                    renderPatternDraftSlot(graphics, draft.input(slot), x + 7 + slot * 18, y + 25, true, slot);
                }
                renderPatternDraftSlot(graphics, draft.output(0), x + 101, y + 25, false, 0);
                // AE2 smithing mode: clear and item substitutions only.
                renderPatternSmallControl(graphics, x + 6, y + 14, 224, 200, PatternControl.CLEAR);
                renderPatternSmallControl(graphics, x + 16, y + 14,
                        draft.substituteItems() ? 224 : 232, 208, PatternControl.ITEM_SUBSTITUTION);
            }
            case STONECUTTING -> {
                graphics.blit(AE2_PATTERN_MODES_TEXTURE, x, y, AE2_PATTERN_PANEL_SRC_X,
                        AE2_STONECUTTING_PANEL_SRC_Y, AE2_PATTERN_PANEL_WIDTH, AE2_PATTERN_PANEL_HEIGHT, 256, 256);
                renderPatternDraftSlot(graphics, draft.input(0), x + 7, y + 25, true, 0);
                // The draft represents one exact recipe id, so show its result as AE2's selected candidate.
                graphics.blit(AE2_PATTERN_MODES_TEXTURE, x + 27, y + 12,
                        AE2_STONECUTTING_SELECTED_SRC_X, AE2_STONECUTTING_SELECTED_SRC_Y,
                        20, 22, 256, 256);
                renderPatternDraftSlot(graphics, draft.output(0), x + 29, y + 15, false, 0);
                graphics.blitSprite(AE2_SMALL_SCROLLER_DISABLED_SPRITE, x + 109, y + 11, 7, 15);
            }
        }

        inspectorPatternControlBounds.add(new PatternControlBounds(PatternControl.RESET, x + 126, y + 5, 54, 16));
        inspectorPatternControlBounds.add(new PatternControlBounds(PatternControl.PRESERVE_ORDER, x + 126, y + 24, 54, 16));
        RecipeTreeTheme.drawButton(graphics, x + 126, y + 5, 54, 16, false, true);
        RecipeTreeTheme.drawButton(graphics, x + 126, y + 24, 54, 16, false, true);
        int resetButtonY = y + 5;
        int orderButtonY = y + 24;
        int centeredTextOffset = (16 - this.font.lineHeight) / 2;
        graphics.drawCenteredString(this.font, Component.translatable("gui.jeict.recipe_tree.pattern_reset"),
                x + 153, resetButtonY + centeredTextOffset, theme.controlText());
        graphics.drawCenteredString(this.font, Component.translatable(draft.preserveInputOrder()
                ? "gui.jeict.recipe_tree.pattern_order_on" : "gui.jeict.recipe_tree.pattern_order_off"),
                x + 153, orderButtonY + centeredTextOffset, theme.controlText());
    }

    private void renderProcessingPatternScrollbar(GuiGraphics graphics, PatternEncodingDraft draft, int x, int y) {
        int trackX = x + 7;
        int trackY = y + 7;
        int trackWidth = 7;
        int trackHeight = 52;
        int thumbHeight = 15;
        int maxScroll = maxPatternDraftScroll(draft);
        int thumbY = trackY + (maxScroll <= 0 ? 0
                : Math.round((trackHeight - thumbHeight) * (patternDraftScroll / (float) maxScroll)));
        graphics.blitSprite(maxScroll <= 0 ? AE2_SMALL_SCROLLER_DISABLED_SPRITE : AE2_SMALL_SCROLLER_SPRITE,
                trackX, thumbY, trackWidth, thumbHeight);
        inspectorPatternControlBounds.add(new PatternControlBounds(
                PatternControl.SCROLLBAR, trackX, trackY, trackWidth, trackHeight));
    }

    private void renderPatternSmallControl(GuiGraphics graphics, int x, int y, int srcX, int srcY,
            PatternControl control) {
        graphics.blit(AE2_STATES_TEXTURE, x, y, srcX, srcY, 8, 8, 256, 256);
        inspectorPatternControlBounds.add(new PatternControlBounds(control, x, y, 8, 8));
    }

    private void renderPatternDraftSlot(GuiGraphics graphics, @Nullable PatternEncodingSlot slot, int x, int y,
            boolean input, int index) {
        // AE2 bakes these slot frames directly into pattern_modes.png. Draw only the slot contents here;
        // overlaying states.png's generic SLOT_BACKGROUND changes the frame style and shifts the item.
        if (slot != null) {
            renderIngredientAt(graphics, slot.ingredient(), x, y);
            String overlay = slot.amount() <= 1L ? "" : formatPatternSlotAmount(slot);
            if (!overlay.isBlank()) {
                renderPatternSlotAmountOverlay(graphics, overlay, x, y);
            }
        }
        inspectorPatternSlotBounds.add(new PatternDraftSlotBounds(input, index, slot, x - 1, y - 1, 18, 18));
    }

    private void renderPatternSlotAmountOverlay(GuiGraphics graphics, String overlay, int x, int y) {
        // Match AE2's StackSizeRenderer: compact white text with a dark shadow, without an opaque backdrop.
        float scale = 0.666F;
        float textX = x + 17.0F - this.font.width(overlay) * scale;
        float textY = y + 15.0F - 5.0F * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 300.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, overlay, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private int maxPatternDraftScroll(PatternEncodingDraft draft) {
        if (draft.mode().isStructured()) return 0;
        int inputRows = Math.max(3, (draft.inputs().size() + 2) / 3);
        int outputRows = Math.max(3, draft.outputs().size());
        return Math.max(0, Math.min(24, Math.max(inputRows, outputRows) - 3));
    }

    private boolean isPatternDraftValid(PatternEncodingDraft draft) {
        boolean hasInput = draft.inputs().stream().anyMatch(slot -> slot != null && slot.ingredient() != null && slot.amount() > 0);
        boolean hasOutput = draft.output(0) != null && draft.output(0).ingredient() != null && draft.output(0).amount() > 0;
        return hasInput && hasOutput;
    }

    private Component patternDraftValidationState(PatternEncodingDraft draft) {
        if (!isPatternDraftValid(draft)) return Component.translatable("gui.jeict.recipe_tree.pattern_invalid");
        if (draft.hasRemovedSourceInput()) return Component.translatable("gui.jeict.recipe_tree.pattern_input_removed");
        if (draft.hasRemovedSourceOutput()) return Component.translatable("gui.jeict.recipe_tree.pattern_output_removed");
        if (draft.primaryOutputChanged()) return Component.translatable("gui.jeict.recipe_tree.pattern_primary_changed");
        return Component.translatable(draft.isDirty()
                ? "gui.jeict.recipe_tree.pattern_modified" : "gui.jeict.recipe_tree.pattern_ready");
    }

    private void renderFooterStatus(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        RecipeTreeOverviewScreen target = focusedTree();
        Component summary = Component.translatable("gui.jeict.recipe_tree.footer_summary",
                target.topMaterials.size() + target.genericTopMaterialRenderData.size(), target.cachedMissingMaterialCount)
                .append(target.planningBusy
                        ? Component.literal("  • ").append(Component.translatable("gui.jeict.recipe_tree.plan_calculating"))
                        : Component.literal("  • ").append(Component.translatable("gui.jeict.recipe_tree.plan_totals",
                                target.planningResult.totalRawUnits(), target.planningResult.totalWasteUnits())));
        graphics.drawString(this.font, summary, canvasLeft() + 6, this.height - 21, theme.metricText(), false);
    }

    private void renderWorkspaceBackdrop(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        graphics.fill(0, 0, this.width, this.height, theme.backgroundOverlay());
        if (this.width <= 10 || this.height <= 10) {
            graphics.fill(0, 0, this.width, this.height, theme.chromeFill());
            return;
        }

        boolean classic = RecipeTreeTheme.isClassicStyle();
        if (!classic) {
            // MEST panels float over the world with a compact two-pixel shadow.
            graphics.fill(7, 7, this.width - 1, this.height - 1, 0x66000000);
            graphics.fill(this.width - 7, 7, this.width - 3, this.height - 3, 0x66000000);
        }
        RecipeTreeTheme.drawRaisedPanel(graphics, 3, 3, this.width - 5, this.height - 5);

        int footerHeight = currentFooterHeight();
        if (classic) {
            RecipeTreeTheme.drawHairline(graphics, 8, this.width - 10, HEADER_HEIGHT - 1);
            RecipeTreeTheme.drawHairline(graphics, 8, this.width - 10, this.height - footerHeight);
        } else {
            // AE2 headers remain part of the light dialog surface, separated by two hairlines.
            graphics.fill(8, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, theme.raisedShadow());
            graphics.fill(8, HEADER_HEIGHT - 1, this.width - 10, HEADER_HEIGHT, theme.raisedHighlight());
            graphics.fill(8, this.height - footerHeight, this.width - 10,
                    this.height - footerHeight + 1, theme.raisedShadow());
            graphics.fill(8, this.height - footerHeight + 1, this.width - 10,
                    this.height - footerHeight + 2, theme.raisedHighlight());
        }

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
        if (!RecipeTreeTheme.isClassicStyle() && right - left > 2 && bottom - top > 2) {
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
        boolean hasActions = (itemSubstitutionButton != null && itemSubstitutionButton.visible)
                || (fluidSubstitutionButton != null && fluidSubstitutionButton.visible)
                || (encodeButton != null && encodeButton.visible)
                || (uploadButton != null && uploadButton.visible);
        return hasActions ? FOOTER_HEIGHT : 8;
    }

    /**
     * Vanilla widgets stay for focus/narration/hit-testing only (they render
     * nothing). This paints AE2 terminal buttons on top.
     */
    private void renderAe2WidgetChrome(GuiGraphics graphics, int mouseX, int mouseY) {
        renderAe2Button(graphics, backButton, mouseX, mouseY);
        renderAe2Button(graphics, projectsButton, mouseX, mouseY);
        renderAe2Button(graphics, planReportButton, mouseX, mouseY);
        renderAe2Button(graphics, undoButton, mouseX, mouseY);
        renderAe2Button(graphics, redoButton, mouseX, mouseY);
        renderAe2Button(graphics, computeQuantitiesButton, mouseX, mouseY);
        renderAe2Button(graphics, styleButton, mouseX, mouseY);
        renderAe2Button(graphics, toggleExistingPatternButton, mouseX, mouseY);
        renderAe2Button(graphics, autoUniqueRecipeButton, mouseX, mouseY);
        renderAe2Button(graphics, memoryReadingButton, mouseX, mouseY);
        renderAe2Button(graphics, strategyButton, mouseX, mouseY);
        renderAe2Button(graphics, autoMergeButton, mouseX, mouseY);
        renderAe2Button(graphics, zoomOutButton, mouseX, mouseY);
        renderAe2Button(graphics, zoomInButton, mouseX, mouseY);
        renderAe2Button(graphics, fitViewButton, mouseX, mouseY);
        renderAe2Button(graphics, settingsButton, mouseX, mouseY);
        renderAe2Button(graphics, itemSubstitutionButton, mouseX, mouseY);
        renderAe2Button(graphics, fluidSubstitutionButton, mouseX, mouseY);
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
        if (button == itemSubstitutionButton || button == fluidSubstitutionButton) {
            CraftingTreeBackend backend = CraftingTreeBackends.get();
            boolean enabled = backend != null && (button == itemSubstitutionButton
                    ? backend.itemSubstituteOn() : backend.fluidSubstituteOn());
            int indicator = enabled ? theme.success() : theme.mutedText();
            graphics.fill(x + 3, y + 3, x + 5, y + height - 3, indicator);
        }
        if (button == settingsButton || (!settingsOpen && isSidebarControlButton(button))) {
            renderSidebarIcon(graphics, button, x + (width - 8) / 2, y + (height - 8) / 2, hovered);
            return;
        }
        if (button == undoButton || button == redoButton) {
            renderHistoryIcon(graphics, button, x, y, width, height, hovered);
            return;
        }
        int textColor = button.active ? (hovered ? theme.controlHoverText() : theme.controlText()) : theme.mutedText();
        Component message = button.getMessage();
        int textX = x + Math.max(2, (width - this.font.width(message)) / 2);
        int textY = y + (height - this.font.lineHeight) / 2;
        graphics.drawString(this.font, message, textX, textY, textColor, false);
    }


    private void renderHistoryIcon(GuiGraphics graphics, Button button, int x, int y, int width, int height, boolean hovered) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        int color = button.active ? (hovered ? theme.controlHoverText() : theme.controlText()) : theme.mutedText();
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        if (button == undoButton) {
            graphics.fill(centerX - 4, centerY - 3, centerX + 4, centerY - 2, color);
            graphics.fill(centerX + 3, centerY - 2, centerX + 4, centerY + 3, color);
            graphics.fill(centerX - 5, centerY - 3, centerX - 2, centerY - 2, color);
            graphics.fill(centerX - 5, centerY - 3, centerX - 4, centerY + 1, color);
        } else {
            graphics.fill(centerX - 4, centerY - 3, centerX + 4, centerY - 2, color);
            graphics.fill(centerX - 4, centerY - 2, centerX - 3, centerY + 3, color);
            graphics.fill(centerX + 2, centerY - 3, centerX + 5, centerY - 2, color);
            graphics.fill(centerX + 4, centerY - 3, centerX + 5, centerY + 1, color);
        }
    }

    private boolean isSidebarControlButton(Button button) {
        return button == autoMergeButton || button == computeQuantitiesButton || button == autoUniqueRecipeButton
                || button == memoryReadingButton || button == strategyButton
                || button == toggleExistingPatternButton || button == styleButton;
    }

    private void renderSidebarIcon(GuiGraphics graphics, Button button, int x, int y, boolean hovered) {
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        boolean enabled = sidebarControlEnabled(button);
        int color = hovered ? theme.controlHoverText() : (enabled ? theme.accent() : theme.mutedText());
        if (button == settingsButton) {
            String symbol = settingsOpen ? "<" : ">";
            graphics.drawCenteredString(this.font, symbol, x + 4, y, color);
            return;
        }
        if (button == styleButton) {
            graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
            graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
            graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
            graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
            return;
        }
        String symbol = button == autoMergeButton ? "M"
                : button == computeQuantitiesButton ? "Q"
                : button == autoUniqueRecipeButton ? "U"
                : button == memoryReadingButton ? "R"
                : button == strategyButton ? "S"
                : button == toggleExistingPatternButton ? "P" : "?";
        graphics.drawCenteredString(this.font, symbol, x + 4, y, color);
        if (!enabled) {
            int slash = hovered ? theme.danger() : theme.controlText();
            for (int offset = 0; offset < 7; offset++) {
                graphics.fill(x + offset, y + 7 - offset, x + offset + 1, y + 8 - offset, slash);
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
                boolean focusedPathEdge = !focusedSearchPath.isEmpty()
                        && focusedSearchPath.contains(edge.parent()) && focusedSearchPath.contains(edge.child());
                if (hasSearchQuery() && !focusedSearchPath.isEmpty() && !focusedPathEdge) continue;
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
                boolean cycleWarning = edge.child().graph().cycleWarning();
                boolean modifiedRecipe = isModifiedPatternRecipe(edge.child().graph().recipeNode());
                int edgeColor = cycleWarning ? theme.danger()
                        : (modifiedRecipe ? theme.modifiedAccent() : (focusedPathEdge ? theme.accent() : theme.edge()));
                graphics.fill(startX, startY, startX + 1, midY, edgeColor);
                graphics.fill(Math.min(startX, endX), midY, Math.max(startX, endX) + 1, midY + 1, edgeColor);
                graphics.fill(endX, midY, endX + 1, endY, edgeColor);
                if (RecipeTreeTheme.isClassicStyle()) {
                    drawEdgeJoint(graphics, startX, midY, edgeColor);
                    drawEdgeJoint(graphics, endX, midY, edgeColor);
                    drawEdgeJoint(graphics, endX, endY - 1, edgeColor);
                }
                if (cycleWarning) {
                    graphics.drawCenteredString(this.font, "↻", endX, Math.max(midY, endY - 11), theme.danger());
                }
            }
        }
    }

    /** Square joint marker that gives classic edges a drafted, plotted-node look. */
    private static void drawEdgeJoint(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
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
                boolean searchMatch = matchesSearch(node.graph());
                if (hasSearchQuery() && !searchMatch && !focusedSearchPath.contains(node)) continue;
                visiblePositionedNodes.add(node);
                lastRenderedNodeCount++;
                boolean modifiedRecipe = isModifiedPatternRecipe(node.graph().recipeNode());
                int accent = node.graph().cycleWarning() ? theme.danger()
                        : (modifiedRecipe ? theme.modifiedAccent()
                        : (searchMatch && hasSearchQuery() ? theme.accent() : (node.graph().showsPatternHint()
                        ? theme.patternHintBorder()
                        : (node.graph().recipeNode() != null ? theme.controlHoverText() : theme.mutedText()))));
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
                int partX = x + ingredientAreaWidth() + NODE_PART_GAP;
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
            int edgeColor = isModifiedLayerRow(rowB) ? theme.modifiedAccent() : theme.edge();
            for (int center : parentCenters) {
                graphics.fill(center, startY, center + 1, midY + 1, edgeColor);
            }
            graphics.fill(minX, midY, maxX + 1, midY + 1, edgeColor);
            for (int center : childCenters) {
                graphics.fill(center, midY, center + 1, endY, edgeColor);
            }
            if (RecipeTreeTheme.isClassicStyle()) {
                for (int center : parentCenters) {
                    drawEdgeJoint(graphics, center, midY, edgeColor);
                }
                for (int center : childCenters) {
                    drawEdgeJoint(graphics, center, midY, edgeColor);
                    drawEdgeJoint(graphics, center, endY - 1, edgeColor);
                }
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
                boolean searchMatch = matchesSearch(material);
                if (hasSearchQuery() && !searchMatch) continue;
                lastRenderedLayerMaterialCount++;
                boolean modifiedRecipe = isModifiedLayerMaterial(material);
                int accent = material.cycleWarning() ? theme.danger()
                        : (modifiedRecipe ? theme.modifiedAccent()
                        : (searchMatch && hasSearchQuery() ? theme.accent() : (material.showsPatternHint()
                        ? theme.patternHintBorder()
                        : (!material.recipeTargets().isEmpty() ? theme.controlHoverText() : theme.mutedText()))));
                RecipeTreeTheme.drawMarkdownNode(graphics, currentX, rowY,
                        currentX + materialWidth, rowY + NODE_HEIGHT, accent);
                if (material.cycleWarning()) {
                    int centerX = currentX + materialWidth / 2;
                    graphics.fill(centerX, rowY - 6, centerX + 1, rowY, theme.danger());
                    graphics.drawCenteredString(this.font, "↻", centerX, rowY - 14, theme.danger());
                }
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
                int partX = currentX + ingredientAreaWidth() + NODE_PART_GAP;
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
                if (detailed && material.collapsible()) {
                    int buttonX = currentX + materialWidth - NODE_PART_PADDING - 10;
                    renderSmallControlIcon(graphics, "-", buttonX, rowY + 9, 10);
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
                        data.cycleWarning() ? theme.danger() : theme.controlHoverText());
                if (data.cycleWarning()) {
                    graphics.drawString(this.font, "↻", currentX + width - 9, y + 2, theme.danger(), false);
                }
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
        RecipeTreeTheme.drawMarkdownNode(graphics, x, y, x + width, y + NODE_HEIGHT,
                data.cycleWarning() ? theme.danger() : accent);
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
        if (data.cycleWarning()) {
            graphics.drawString(this.font, "↻", x + width - 9, y + 2, theme.danger(), false);
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

    private int ingredientAreaWidth() {
        return NODE_MAIN_SIZE;
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

    private void togglePatternSubstitution(boolean fluid) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsSubstitution()) {
            return;
        }
        if (fluid) {
            backend.toggleFluidSubstitute();
        } else {
            backend.toggleItemSubstitute();
        }
    }

    private @Nullable List<Component> patternSubstitutionTooltipAt(int mouseX, int mouseY) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsSubstitution()) {
            return null;
        }
        if (isPointInsideButton(itemSubstitutionButton, mouseX, mouseY)) {
            return substitutionTooltipLines(backend, false);
        }
        if (isPointInsideButton(fluidSubstitutionButton, mouseX, mouseY)) {
            return substitutionTooltipLines(backend, true);
        }
        return null;
    }

    private List<Component> substitutionTooltipLines(CraftingTreeBackend backend, boolean fluid) {
        List<Component> backendLines = backend.substitutionTooltip(fluid);
        if (backendLines != null && !backendLines.isEmpty()) {
            return backendLines;
        }
        boolean enabled = fluid ? backend.fluidSubstituteOn() : backend.itemSubstituteOn();
        return List.of(Component.translatable(fluid
                        ? "gui.jeict.recipe_tree.fluid_substitution"
                        : "gui.jeict.recipe_tree.item_substitution"),
                Component.translatable(enabled
                        ? "gui.jeict.recipe_tree.substitution_enabled"
                        : "gui.jeict.recipe_tree.substitution_disabled").withStyle(ChatFormatting.GRAY));
    }


    private @Nullable Component headerControlTooltipAt(double mouseX, double mouseY) {
        if (isPointInsideButton(settingsButton, mouseX, mouseY)) {
            return Component.translatable(settingsOpen
                    ? "gui.jeict.recipe_tree.settings_collapse_tooltip"
                    : "gui.jeict.recipe_tree.settings_expand_tooltip");
        }
        if (isPointInsideButton(projectsButton, mouseX, mouseY)) {
            return Component.translatable("gui.jeict.recipe_tree.projects_tooltip");
        }
        if (isPointInsideButton(planReportButton, mouseX, mouseY)) {
            return Component.translatable("gui.jeict.recipe_tree.plan_tooltip");
        }
        if (isPointInsideButton(undoButton, mouseX, mouseY)) {
            return Component.translatable("key.jeict.undo");
        }
        if (isPointInsideButton(redoButton, mouseX, mouseY)) {
            return Component.translatable("key.jeict.redo");
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
            if (hasCycleWarning(bounds.material())) {
                lines.add(Component.translatable("gui.jeict.recipe_tree.cycle_warning").withStyle(ChatFormatting.RED));
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
            if (bounds.data().cycleWarning()) {
                lines.add(Component.translatable("gui.jeict.recipe_tree.cycle_warning").withStyle(ChatFormatting.RED));
            }
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

    private void renderWorkspaceTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY,
            int mouseX, int mouseY) {
        RecipeTreeOverviewScreen focused = focusedTree();
        if (focused != this && mouseX >= this.width - INSPECTOR_WIDTH - 8 && focused.hasInspectorSelection()) {
            focused.renderTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
            return;
        }
        for (WorkspaceTreePlacement placement : workspaceTreePlacements()) {
            double localX = logicalMouseX - placement.offsetX();
            double localY = logicalMouseY - placement.offsetY();
            if (placement.screen().currentTreeLogicalBounds().expanded(48.0D, 130.0D).contains(localX, localY)) {
                placement.screen().renderTooltip(graphics, localX, localY, mouseX, mouseY);
                return;
            }
        }
        renderTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
    }

    private void renderTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY, int mouseX, int mouseY) {
        for (PatternDraftSlotBounds bounds : inspectorPatternSlotBounds) {
            if (isInsideInspectorViewport(mouseX, mouseY) && bounds.contains(mouseX, mouseY)) {
                PatternEncodingSlot slot = bounds.slot();
                List<Component> lines = slot == null
                        ? new ArrayList<>()
                        : new ArrayList<>(ingredientTooltipLines(slot.ingredient()));
                if (lines.isEmpty()) {
                    lines.add(Component.translatable("gui.jeict.recipe_tree.pattern_empty_slot"));
                }
                if (slot != null) {
                    lines.add(Component.translatable("gui.jeict.recipe_tree.pattern_slot_amount", slot.amount())
                            .withStyle(ChatFormatting.GRAY));
                    if (slot.hasAlternatives()) {
                        lines.add(Component.translatable("gui.jeict.recipe_tree.pattern_cycle_alternative")
                                .withStyle(ChatFormatting.AQUA));
                    }
                }
                lines.add(Component.translatable("gui.jeict.recipe_tree.pattern_slot_controls")
                        .withStyle(ChatFormatting.DARK_GRAY));
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        for (PatternControlBounds bounds : inspectorPatternControlBounds) {
            if (isInsideInspectorViewport(mouseX, mouseY) && bounds.contains(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, List.of(patternControlTooltip(bounds.control())), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
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
        Component headerTooltip = headerControlTooltipAt(mouseX, mouseY);
        if (headerTooltip != null) {
            graphics.renderTooltip(this.font, List.of(headerTooltip), Optional.empty(), mouseX, mouseY);
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
            if (strategyButton.visible && isPointInsideButton(strategyButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.overview_strategy_tooltip")),
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
                    if (bounds.material().cycleWarning()) {
                        lines.add(Component.translatable("gui.jeict.recipe_tree.cycle_warning").withStyle(ChatFormatting.RED));
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
        if (strategyButton.visible && isPointInsideButton(strategyButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.jeict.recipe_tree.overview_strategy_tooltip")),
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
        if (autoMergeSameMaterials) {
            LayerMaterialBounds layer = findLayerAt(logicalMouseX, logicalMouseY);
            if (layer != null && isLayerCollapseButtonAt(layer, logicalMouseX, logicalMouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.jeict.recipe_tree.collapse_branch")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
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
            int machineX = x + ingredientAreaWidth() + NODE_PART_GAP;
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
                if (node.graph().cycleWarning()) {
                    lines.add(Component.translatable("gui.jeict.recipe_tree.cycle_warning").withStyle(ChatFormatting.RED));
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

    private boolean handlePatternDraftSlotClick(PatternDraftSlotBounds bounds, int button) {
        RecipeTreeRecipeViewModel recipe = selectedInspectorRecipe();
        if (recipe == null) return false;
        PatternEncodingDraft draft = patternDraftFor(recipe);
        boolean protectedStructuredSlot = draft.mode().isStructured();
        if (button == 1) {
            if (!protectedStructuredSlot) {
                if (bounds.input()) draft.setInput(bounds.index(), null);
                else draft.setOutput(bounds.index(), null);
                markPatternDraftTreeChanged(recipe, draft);
            }
            return true;
        }
        if (button == 2) {
            if (bounds.slot() != null && !protectedStructuredSlot) openPatternAmountEditor(bounds);
            return true;
        }
        if (button != 0) return false;

        if (!bounds.input() && Screen.hasShiftDown() && bounds.index() > 0 && bounds.slot() != null) {
            draft.promoteOutput(bounds.index());
            markPatternDraftTreeChanged(recipe, draft);
            return true;
        }

        ItemStack replacement = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        if (!replacement.isEmpty() && (bounds.slot() == null || Screen.hasShiftDown()) && !protectedStructuredSlot) {
            var runtime = JeiCraftingTreePlugin.getJeiRuntime();
            ITypedIngredient<?> typed = runtime == null ? null
                    : runtime.getIngredientManager().createTypedIngredient(replacement.copyWithCount(1), true).orElse(null);
            if (typed != null) {
                PatternEncodingSlot replacementSlot = new PatternEncodingSlot(List.of(typed),
                        bounds.slot() == null ? Math.max(1, replacement.getCount()) : bounds.slot().amount());
                if (bounds.input()) draft.setInput(bounds.index(), replacementSlot);
                else draft.setOutput(bounds.index(), replacementSlot);
                markPatternDraftTreeChanged(recipe, draft);
            }
            return true;
        }
        if (bounds.slot() != null && bounds.slot().hasAlternatives()) {
            bounds.slot().cycleAlternative(1);
            markPatternDraftTreeChanged(recipe, draft);
        }
        return true;
    }

    private boolean handlePatternControlClick(PatternControlBounds bounds, double mouseY) {
        RecipeTreeRecipeViewModel recipe = selectedInspectorRecipe();
        if (recipe == null) return false;
        PatternEncodingDraft draft = patternDraftFor(recipe);
        switch (bounds.control()) {
            case CLEAR -> {
                draft.clear();
                playUiButtonClick();
                markPatternDraftTreeChanged(recipe, draft);
                return true;
            }
            case CYCLE_OUTPUT -> {
                draft.cyclePrimaryOutput();
                playUiButtonClick();
                markPatternDraftTreeChanged(recipe, draft);
                return true;
            }
            case ITEM_SUBSTITUTION -> draft.setSubstituteItems(!draft.substituteItems());
            case FLUID_SUBSTITUTION -> draft.setSubstituteFluids(!draft.substituteFluids());
            case RESET -> {
                playUiButtonClick();
                resetPatternDraft(recipe);
                return true;
            }
            case PRESERVE_ORDER -> draft.setPreserveInputOrder(!draft.preserveInputOrder());
            case SCROLLBAR -> {
                int max = maxPatternDraftScroll(draft);
                double fraction = Math.max(0.0D, Math.min(1.0D, (mouseY - bounds.y() - 5.0D) / Math.max(1.0D, bounds.height() - 10.0D)));
                patternDraftScroll = (int) Math.round(fraction * max);
                return true;
            }
        }
        playUiButtonClick();
        markPatternDraftChanged(recipe, draft);
        return true;
    }

    private void playUiButtonClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void markPatternDraftChanged(@Nullable RecipeTreeRecipeViewModel recipe, PatternEncodingDraft draft) {
        if (recipe == null) return;
        existingPatternRecipeCache.remove(recipe);
        String key = patternRecipeKey(sourceRecipeForPatternDraft(recipe));
        if (draft.isDirty()) modifiedPatternRecipeKeys.add(key);
        else modifiedPatternRecipeKeys.remove(key);
    }

    private static String patternRecipeKey(RecipeTreeRecipeViewModel recipe) {
        if (recipe.recipeId() != null) return "id#" + recipe.recipeId();
        // Output amounts are editable, so they must not be part of the persistent modification marker key.
        return "view#" + recipe.title().getString() + "#"
                + ItemStack.hashItemAndComponents(recipe.primaryOutput());
    }

    private boolean isModifiedPatternRecipe(@Nullable RecipeTreeNodeViewModel node) {
        if (node == null) return false;
        RecipeTreeRecipeViewModel recipe = node.recipe();
        RecipeTreeRecipeViewModel sourceRecipe = sourceRecipeForPatternDraft(recipe);
        return node.isPatternModified()
                || modifiedPatternNodes.contains(node)
                || modifiedPatternRecipeKeys.contains(patternRecipeKey(recipe))
                || modifiedPatternRecipeKeys.contains(patternRecipeKey(sourceRecipe));
    }

    private boolean isModifiedLayerMaterial(LayerMaterial material) {
        for (RecipeTreeNodeViewModel target : material.recipeTargets()) {
            if (isModifiedPatternRecipe(target)) return true;
        }
        return false;
    }

    private boolean isModifiedLayerRow(LayerRow row) {
        for (LayerMaterial material : row.materials()) {
            if (isModifiedLayerMaterial(material)) return true;
        }
        return false;
    }

    private void markPatternDraftTreeChanged(@Nullable RecipeTreeRecipeViewModel recipe, PatternEncodingDraft draft) {
        markPatternDraftChanged(recipe, draft);
        if (recipe == null) return;
        syncPatternDraftToRecipeTree(recipe, draft);
    }

    private void syncPatternDraftToRecipeTree(RecipeTreeRecipeViewModel recipe, PatternEncodingDraft draft) {
        RecipeTreeNodeViewModel inspectorTarget = selectedInspectorRecipeNode();
        String targetIdentity = draft.sourceRecipeIdentity();
        boolean changed = false;
        ArrayDeque<RecipeTreeNodeViewModel> pending = new ArrayDeque<>();
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        pending.addLast(context.root());
        while (!pending.isEmpty()) {
            RecipeTreeNodeViewModel node = pending.removeLast();
            if (!visited.add(node)) continue;
            if (targetIdentity.equals(node.recipe().stableIdentity()) || node.recipe().sameRecipeAs(recipe)) {
                RecipeTreeRecipeViewModel sourceRecipe = patternDraftSourceRecipes.getOrDefault(
                        targetIdentity, sourceRecipeForPatternDraft(recipe));
                RecipeTreeRecipeViewModel updatedRecipe = recipeWithPatternDraft(node.recipe(), draft);
                node.setRecipe(updatedRecipe);
                // Keep an exact alias for edited no-id recipes whose stable identity changes with output amount.
                patternDraftSourceRecipes.put(updatedRecipe.stableIdentity(), sourceRecipe);
                if (draft.isDirty()) modifiedPatternNodes.add(node);
                else modifiedPatternNodes.remove(node);
                node.setPatternModified(draft.isDirty());
                changed = true;
            }
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                if (input.child() != null) pending.addLast(input.child());
            }
        }
        if (changed) {
            rebuildLayout();
            restoreInspectorSelection(inspectorTarget);
        }
    }

    private RecipeTreeRecipeViewModel recipeWithPatternDraft(RecipeTreeRecipeViewModel current, PatternEncodingDraft draft) {
        Map<String, RecipeTreeNodeViewModel> childrenBySignature = new HashMap<>();
        for (RecipeTreeInputViewModel input : current.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) childrenBySignature.putIfAbsent(signatureOf(input), child);
        }

        List<RecipeTreeInputViewModel> inputs = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < draft.inputs().size(); slotIndex++) {
            PatternEncodingSlot slot = draft.inputs().get(slotIndex);
            if (slot == null || slot.ingredient() == null) continue;
            int patternSlotIndex = draft.mode() == PatternEncodingMode.CRAFTING ? slotIndex : -1;
            RecipeTreeInputViewModel input = inputFromPatternSlot(slot, true, patternSlotIndex);
            RecipeTreeNodeViewModel child = childrenBySignature.get(signatureOf(input));
            if (child != null) input.setChild(child);
            inputs.add(input);
        }

        List<RecipeTreeOutputViewModel> outputs = new ArrayList<>();
        for (int i = 0; i < draft.outputs().size(); i++) {
            PatternEncodingSlot slot = draft.outputs().get(i);
            if (slot == null || slot.ingredient() == null) continue;
            outputs.add(outputFromPatternSlot(slot, i == 0));
        }
        if (outputs.isEmpty()) {
            outputs = new ArrayList<>(current.outputs());
        }
        RecipeTreeOutputViewModel primary = outputs.getFirst().withPrimary(true);
        outputs.set(0, primary);
        return new RecipeTreeRecipeViewModel(primary.ingredient(), primary.itemStack(), primary.amount(), outputs,
                current.title(), current.subtitle(), current.subtitleIcon(), current.recipeId(), inputs);
    }

    private RecipeTreeInputViewModel inputFromPatternSlot(PatternEncodingSlot slot, boolean consumed) {
        return inputFromPatternSlot(slot, consumed, -1);
    }

    private RecipeTreeInputViewModel inputFromPatternSlot(PatternEncodingSlot slot, boolean consumed, int patternSlotIndex) {
        List<DisplayOption> options = displayOptionsFromPatternSlot(slot);
        RequestedIngredient requested = requestedIngredientFromPatternSlot(slot);
        RecipeTreeInputViewModel input = new RecipeTreeInputViewModel(requested, options, slot.amount(),
                amountTextForPatternSlot(slot), consumed, patternSlotIndex);
        input.selectAlternative(slot.selectedAlternative());
        return input;
    }

    private RecipeTreeOutputViewModel outputFromPatternSlot(PatternEncodingSlot slot, boolean primary) {
        ITypedIngredient<?> ingredient = slot.ingredient();
        return new RecipeTreeOutputViewModel(ingredient, itemStackFromTypedIngredient(ingredient), slot.amount(), 1.0D, primary);
    }

    private List<DisplayOption> displayOptionsFromPatternSlot(PatternEncodingSlot slot) {
        IIngredientManager ingredientManager = getIngredientManager();
        List<DisplayOption> options = new ArrayList<>();
        for (ITypedIngredient<?> alternative : slot.alternatives()) {
            options.add(new DisplayOption(alternative, displayNameForTypedIngredient(ingredientManager, alternative),
                    itemStackFromTypedIngredient(alternative)));
        }
        return List.copyOf(options);
    }

    private @Nullable RequestedIngredient requestedIngredientFromPatternSlot(PatternEncodingSlot slot) {
        List<ItemStack> alternatives = new ArrayList<>();
        for (ITypedIngredient<?> alternative : slot.alternatives()) {
            ItemStack stack = itemStackFromTypedIngredient(alternative);
            if (!stack.isEmpty()) alternatives.add(stack);
        }
        return alternatives.isEmpty() ? null : new RequestedIngredient(alternatives, (int) Math.min(Integer.MAX_VALUE, slot.amount()));
    }

    private String amountTextForPatternSlot(PatternEncodingSlot slot) {
        ITypedIngredient<?> ingredient = slot.ingredient();
        Object raw = ingredient == null ? null : ingredient.getIngredient();
        if (raw instanceof FluidStack) {
            return formatMilliBuckets(slot.amount());
        }
        if (GenericIngredientUtil.tryGetMekanismChemicalAmount(raw) > 0L) {
            return formatChemicalAmount(slot.amount());
        }
        return Component.translatable("gui.jeict.recipe_tree.amount_exact", Math.max(1L, slot.amount())).getString();
    }

    private String displayNameForTypedIngredient(@Nullable IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        if (ingredientManager != null) {
            return displayNameForTypedIngredientTyped(ingredientManager, ingredient);
        }
        Object raw = ingredient.getIngredient();
        if (raw instanceof ItemStack stack) return stack.getHoverName().getString();
        return String.valueOf(raw);
    }

    private static <T> String displayNameForTypedIngredientTyped(IIngredientManager ingredientManager,
            ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return ingredientManager.getIngredientHelper(typed.getType()).getDisplayName(typed.getIngredient());
    }

    private ItemStack itemStackFromTypedIngredient(@Nullable ITypedIngredient<?> ingredient) {
        if (ingredient != null && ingredient.getIngredient() instanceof ItemStack stack) {
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private void openPatternAmountEditor(PatternDraftSlotBounds bounds) {
        if (patternAmountEditor == null || bounds.slot() == null) return;
        patternAmountEditTarget = bounds;
        patternAmountEditor.setX(patternAmountEditorX);
        patternAmountEditor.setY(patternAmountEditorY);
        patternAmountEditor.setValue(Long.toString(bounds.slot().amount()));
        patternAmountEditor.visible = true;
        patternAmountEditor.setFocused(true);
        patternAmountEditor.moveCursorToEnd(false);
        this.setFocused(patternAmountEditor);
    }

    private void commitPatternAmountEditor() {
        if (patternAmountEditor == null || patternAmountEditTarget == null) {
            closePatternAmountEditor(false);
            return;
        }
        try {
            long amount = Long.parseLong(patternAmountEditor.getValue());
            RecipeTreeRecipeViewModel recipe = selectedInspectorRecipe();
            if (amount > 0 && recipe != null) {
                PatternEncodingDraft draft = patternDraftFor(recipe);
                PatternEncodingSlot slot = patternAmountEditTarget.input()
                        ? draft.input(patternAmountEditTarget.index()) : draft.output(patternAmountEditTarget.index());
                if (slot != null) {
                    slot.setAmount(amount);
                    markPatternDraftTreeChanged(recipe, draft);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        closePatternAmountEditor(true);
    }

    private void closePatternAmountEditor(boolean keepValue) {
        if (patternAmountEditor != null) {
            patternAmountEditor.visible = false;
            patternAmountEditor.setFocused(false);
            if (!keepValue) patternAmountEditor.setValue("");
        }
        patternAmountEditTarget = null;
        if (this.getFocused() == patternAmountEditor) this.setFocused(null);
    }

    private Component patternControlTooltip(PatternControl control) {
        return Component.translatable(switch (control) {
            case CLEAR -> "gui.jeict.recipe_tree.pattern_clear";
            case CYCLE_OUTPUT -> "gui.jeict.recipe_tree.pattern_cycle_output";
            case ITEM_SUBSTITUTION -> "gui.jeict.recipe_tree.pattern_item_substitution";
            case FLUID_SUBSTITUTION -> "gui.jeict.recipe_tree.pattern_fluid_substitution";
            case RESET -> "gui.jeict.recipe_tree.pattern_reset_tooltip";
            case PRESERVE_ORDER -> "gui.jeict.recipe_tree.pattern_order_tooltip";
            case SCROLLBAR -> "gui.jeict.recipe_tree.pattern_scroll";
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && focusedEditor.pendingAlternativeSelection != null
                && focusedEditor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (focusedEditor != this && mouseX >= this.width - INSPECTOR_WIDTH - 8
                && focusedEditor.hasInspectorSelection()) {
            if (focusedEditor.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (patternAmountEditor != null && patternAmountEditor.visible
                && patternAmountEditor.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && inspectorMaxScroll > 0
                && mouseX >= this.width - 14 && mouseX < this.width - 6
                && mouseY >= inspectorScrollbarTrackTop
                && mouseY < inspectorScrollbarTrackTop + inspectorScrollbarTrackHeight) {
            if (patternAmountEditor != null && patternAmountEditor.visible) {
                commitPatternAmountEditor();
            }
            if (mouseY >= inspectorScrollbarThumbTop
                    && mouseY < inspectorScrollbarThumbTop + inspectorScrollbarThumbHeight) {
                inspectorScrollbarDragOffset = mouseY - inspectorScrollbarThumbTop;
            } else {
                inspectorScrollbarDragOffset = inspectorScrollbarThumbHeight * 0.5D;
                updateInspectorScrollFromThumb(mouseY - inspectorScrollbarDragOffset);
            }
            draggingInspectorScrollbar = true;
            return true;
        }
        for (PatternDraftSlotBounds bounds : inspectorPatternSlotBounds) {
            if (isInsideInspectorViewport(mouseX, mouseY)
                    && bounds.contains(mouseX, mouseY) && handlePatternDraftSlotClick(bounds, button)) {
                return true;
            }
        }
        for (PatternControlBounds bounds : inspectorPatternControlBounds) {
            if (isInsideInspectorViewport(mouseX, mouseY)
                    && bounds.contains(mouseX, mouseY) && handlePatternControlClick(bounds, mouseY)) {
                return true;
            }
        }
        if (patternAmountEditor != null && patternAmountEditor.visible) {
            commitPatternAmountEditor();
        }
        if (button == 0 && isPointInsideBatchBadge(mouseX, mouseY)) {
            return true;
        }
        boolean insideWorkspace = isInsideWorkspace(mouseX, mouseY);
        double logicalMouseXForPin = (mouseX - panX) / zoom;
        double logicalMouseYForPin = (mouseY - panY) / zoom;
        if (insideWorkspace && currentTreeLogicalBounds().expanded(48.0D, 130.0D)
                .contains(logicalMouseXForPin, logicalMouseYForPin)) {
            focusedWorkspaceTree = this;
            workspace.activate(context);
            syncToggleExistingPatternButton();
        }
        if (workspaceHost == null && insideWorkspace && (button == 0 || button == 1)
                && routeWorkspaceTreeClick(mouseX, mouseY, button)) {
            return true;
        }
        if (insideWorkspace && button == 0
                && handleWorkspaceLinkClick(logicalMouseXForPin, logicalMouseYForPin)) {
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
                if (button == 0 && isLayerCollapseButtonAt(clicked, logicalMouseX, logicalMouseY)) {
                    collapseLayerMaterial(clicked.material());
                    closeSelection();
                    rebuildLayout();
                    return true;
                }
                if (button == 0 && handleMergedLayerMaterialClick(clicked.material())) {
                    return true;
                }
                if (button == 1) {
                    selectedLayerMaterial = clicked.material();
                    selectedNode = null;
                    ensureLogicalRectVisible(clicked.x(), clicked.y(), clicked.width(), clicked.height());
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
                if (button == 1) {
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
        RecipeTreeNodeViewModel recipeNode = node.graph().recipeNode();
        return recipeNode != null && recipeNode.parent() != null
                && (!node.graph().children().isEmpty() || recipeNode.recipe().inputs().isEmpty());
    }

    private boolean shouldBlockExpansion(MergedLeaf leaf) {
        return context.disableExistingPatternExpansion() && hasExistingPatternForLeaf(leaf);
    }

    private boolean shouldBlockExpansion(RecipeTreeNodeViewModel node) {
        return context.disableExistingPatternExpansion() && hasExistingPatternForOutput(node.recipe());
    }

    private boolean shouldBlockExpansion(RecipeTreeInputViewModel input) {
        if (!context.disableExistingPatternExpansion()) {
            return false;
        }
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsExistingPatternHints()) {
            return false;
        }
        String signature = signatureOf(input);
        return existingPatternMaterialCache.computeIfAbsent(signature, ignored -> {
            ITypedIngredient<?> displayed = input.displayIngredient();
            if (displayed != null) {
                return backend.isCraftable(displayed.getIngredient());
            }
            return queryExistingPatternForRequestedIngredient(backend, input.requestedIngredient());
        });
    }

    private boolean shouldBlockExpansion(LayerMaterial material) {
        if (!context.disableExistingPatternExpansion()) {
            return false;
        }
        for (RecipeTreeNodeViewModel target : material.recipeTargets()) {
            if (hasExistingPatternForOutput(target.recipe())) {
                return true;
            }
        }
        MergedLeaf leaf = mergedLeafFromLayerMaterial(material);
        return leaf != null && hasExistingPatternForLeaf(leaf);
    }

    private void showExistingPatternExpansionBlockedMessage() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_existing_pattern_blocked")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && mouseX >= this.width - INSPECTOR_WIDTH - 8
                && focusedEditor.hasInspectorSelection()) {
            return focusedEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        RecipeTreeRecipeViewModel inspectorRecipe = selectedInspectorRecipe();
        PatternEncodingDraft hoveredDraft = inspectorRecipe == null ? null : patternDraftFor(inspectorRecipe);
        PatternDraftSlotBounds hoveredSlot = hoveredDraft == null || !isInsideInspectorViewport(mouseX, mouseY)
                ? null : inspectorPatternSlotBounds.stream()
                        .filter(bounds -> bounds.contains(mouseX, mouseY)).findFirst().orElse(null);
        if (hoveredSlot != null) {
            if (hoveredSlot.slot() != null && !hoveredDraft.mode().isStructured()) {
                int amountDirection = (int) Math.signum(scrollY);
                if (amountDirection != 0) {
                    long step = Screen.hasShiftDown() ? 64L : 1L;
                    hoveredSlot.slot().setAmount(Math.max(1L,
                            hoveredSlot.slot().amount() + amountDirection * step));
                    markPatternDraftTreeChanged(inspectorRecipe, hoveredDraft);
                }
            }
            // A wheel event over a pattern slot always belongs to the editor, never to the tree zoom.
            return true;
        }
        if (hoveredDraft != null && isInsideInspectorViewport(mouseX, mouseY)
                && inspectorPatternControlBounds.stream().anyMatch(bounds -> bounds.contains(mouseX, mouseY))) {
            int direction = -(int) Math.signum(scrollY);
            patternDraftScroll = Math.max(0,
                    Math.min(maxPatternDraftScroll(hoveredDraft), patternDraftScroll + direction));
            return true;
        }
        if (isInsideInspectorViewport(mouseX, mouseY)) {
            int direction = (int) Math.signum(scrollY);
            if (direction != 0) {
                if (patternAmountEditor != null && patternAmountEditor.visible) {
                    commitPatternAmountEditor();
                }
                inspectorScroll = Math.max(0, Math.min(inspectorMaxScroll,
                        inspectorScroll - direction * INSPECTOR_SCROLL_STEP));
            }
            // The inspector owns wheel input even when all of its content currently fits.
            return true;
        }
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
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && focusedEditor.draggingInspectorScrollbar) {
            return focusedEditor.mouseReleased(mouseX, mouseY, button);
        }
        if (button == 0 && draggingInspectorScrollbar) {
            draggingInspectorScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && focusedEditor.draggingInspectorScrollbar) {
            return focusedEditor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && draggingInspectorScrollbar) {
            updateInspectorScrollFromThumb(mouseY - inspectorScrollbarDragOffset);
            return true;
        }
        if (button == 0 && isInsideWorkspace(mouseX, mouseY)) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateInspectorScrollFromThumb(double requestedThumbTop) {
        int thumbTravel = Math.max(0, inspectorScrollbarTrackHeight - inspectorScrollbarThumbHeight);
        if (thumbTravel == 0 || inspectorMaxScroll <= 0) {
            inspectorScroll = 0;
            return;
        }
        double relative = Math.max(0.0D, Math.min(thumbTravel,
                requestedThumbTop - inspectorScrollbarTrackTop));
        inspectorScroll = (int) Math.round(relative / thumbTravel * inspectorMaxScroll);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean hasSearchQuery() {
        return searchBox != null && !searchBox.getValue().isBlank();
    }

    private boolean matchesSearch(GraphNode graph) {
        if (!hasSearchQuery()) return true;
        String query = searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (graph.recipeNode() != null && RecipeTreeSearchIndex.matches(graph.recipeNode().recipe(), query)) return true;
        String machine = graph.machineName() == null ? "" : graph.machineName().getString();
        return searchTextMatches(graph.title(), machine, query);
    }

    private boolean matchesSearch(LayerMaterial material) {
        if (!hasSearchQuery()) return true;
        String query = searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        String machine = material.machineName() == null ? "" : material.machineName().getString();
        if (searchTextMatches(material.label(), machine, query)) return true;
        return material.recipeTargets().stream().anyMatch(node -> RecipeTreeSearchIndex.matches(node.recipe(), query));
    }

    private boolean searchTextMatches(String label, String machine, String query) {
        String value = query;
        if (query.startsWith("#")) return machine.toLowerCase(java.util.Locale.ROOT).contains(query.substring(1));
        if (query.startsWith("@")) value = query.substring(1);
        return JustEnoughCharactersCompat.contains(label, value)
                || JustEnoughCharactersCompat.contains(machine, value);
    }

    private void focusNextSearchResult() {
        if (!hasSearchQuery()) return;
        List<RecipeTreeNodeViewModel> matches = RecipeTreeSearchIndex.matchingNodes(context.root(), searchBox.getValue());
        if (matches.isEmpty()) return;
        searchResultIndex = (searchResultIndex + 1) % matches.size();
        RecipeTreeNodeViewModel target = matches.get(searchResultIndex);
        for (PositionedNode positioned : positionedNodes) {
            if (positioned.graph().recipeNode() == target) {
                selectedNode = positioned;
                rebuildFocusedSearchPath(positioned);
                panX = width * 0.5D - (positioned.x() + positioned.graph().width() * 0.5D) * zoom;
                panY = height * 0.5D - (positioned.y() + NODE_HEIGHT * 0.5D) * zoom;
                return;
            }
        }
        for (LayerRow row : mergedLayerRows) for (LayerMaterial material : row.materials()) {
            if (material.recipeTargets().contains(target)) {
                selectedLayerMaterial = material;
                return;
            }
        }
    }


    private void rebuildFocusedSearchPath(PositionedNode target) {
        focusedSearchPath.clear();
        PositionedNode cursor = target;
        focusedSearchPath.add(cursor);
        while (true) {
            PositionedNode current = cursor;
            Edge parentEdge = edges.stream().filter(edge -> edge.child().equals(current)).findFirst().orElse(null);
            if (parentEdge == null) return;
            cursor = parentEdge.parent();
            if (!focusedSearchPath.add(cursor)) return;
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && focusedEditor.patternAmountEditor != null
                && focusedEditor.patternAmountEditor.visible) {
            return focusedEditor.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        RecipeTreeOverviewScreen focusedEditor = focusedTree();
        if (focusedEditor != this && focusedEditor.patternAmountEditor != null
                && focusedEditor.patternAmountEditor.visible
                && focusedEditor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) {
            LOGGER.info("[JEICT-TREE] escape pressed; editorVisible={} searchFocused={} return={}",
                    patternAmountEditor != null && patternAmountEditor.visible,
                    searchBox != null && searchBox.isFocused(),
                    returnScreen == null ? "<null>" : returnScreen.getClass().getName());
        }
        if (patternAmountEditor != null && patternAmountEditor.visible) {
            if (keyCode == 257 || keyCode == 335) {
                commitPatternAmountEditor();
                return true;
            }
            if (keyCode == 256) {
                closePatternAmountEditor(false);
                return true;
            }
        }
        if (searchBox != null && searchBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            focusNextSearchResult();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void undoLastEdit() {
        if (history.undo(context.projects())) rebuildLayout();
    }

    public void redoLastEdit() {
        if (history.redo(context.projects())) rebuildLayout();
    }

    public void focusSearchField() {
        if (searchBox != null) {
            searchBox.setFocused(true);
            this.setFocused(searchBox);
        }
    }

    private void recordHistory() {
        history.record(context.projects());
    }

    private void openProjects() {
        this.minecraft.setScreen(new RecipeTreeProjectScreen(this, context.projects(), this::recordHistory,
                this::rebuildLayout));
    }

    private void cycleSubstitutionStrategy() {
        SubstitutionStrategy current = RecipeTreeConfig.SUBSTITUTION_STRATEGY.get();
        SubstitutionStrategy[] strategies = SubstitutionStrategy.values();
        SubstitutionStrategy next = strategies[(current.ordinal() + 1) % strategies.length];
        RecipeTreeConfig.SUBSTITUTION_STRATEGY.set(next);
        syncStrategyButton();
        requestPlanning();
    }

    private void syncStrategyButton() {
        if (strategyButton == null) return;
        strategyButton.setMessage(Component.translatable("gui.jeict.recipe_tree.overview_strategy",
                RecipeTreeConfig.SUBSTITUTION_STRATEGY.get().name()));
    }

    private void openPlanReport() {
        this.minecraft.setScreen(new RecipeTreePlanReportScreen(this, planningResult, planningTargets,
                ClientInventorySnapshotCache.get(), RecipeTreeConfig.SUBSTITUTION_STRATEGY.get()));
    }

    private void requestPlanning() {
        long inventoryVersion = ClientInventorySnapshotCache.version();
        long fingerprint = planningFingerprint();
        if (fingerprint == planningFingerprint && inventoryVersion == planningInventoryVersion) return;
        planningFingerprint = fingerprint;
        planningInventoryVersion = inventoryVersion;
        InventorySnapshot inventory = ClientInventorySnapshotCache.get();
        planningTargets = RecipeTreePlanAdapter.targets(context.projects().roots(), context.projects().amounts());
        planningBusy = true;
        planService.submit(planningTargets, inventory,
                RecipeTreeConfig.SUBSTITUTION_STRATEGY.get(), RecipeTreeConfig.PREFERRED_NAMESPACE.get(), result -> {
                    planningResult = result;
                    planningBusy = false;
                });
    }

    private long planningFingerprint() {
        long hash = 17L;
        hash = 31L * hash + RecipeTreeConfig.SUBSTITUTION_STRATEGY.get().ordinal();
        hash = 31L * hash + RecipeTreeConfig.PREFERRED_NAMESPACE.get().hashCode();
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, RecipeTreeNodeViewModel> entry : context.projects().roots().entrySet()) {
            hash = 31L * hash + entry.getKey().hashCode();
            hash = 31L * hash + Long.hashCode(context.projects().amounts().getOrDefault(entry.getKey(), 1L));
            hash = fingerprintNode(entry.getValue(), hash, visited);
        }
        return hash;
    }

    private long fingerprintNode(RecipeTreeNodeViewModel node, long hash, Set<RecipeTreeNodeViewModel> visited) {
        if (!visited.add(node)) return 31L * hash + 1L;
        hash = 31L * hash + node.recipe().stableIdentity().hashCode();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            hash = 31L * hash + input.selectedAlternativeIndex();
            RecipeTreeNodeViewModel child = input.child();
            hash = 31L * hash + (child == null ? 0L : 1L);
            if (child != null) hash = fingerprintNode(child, hash, visited);
        }
        return hash;
    }

    /** Releases per-screen workers when this workspace view is permanently replaced. */
    public void releaseWorkspaceResources() {
        pendingJeiSelection = null;
        pendingAlternativeSelection = null;
        planService.close();
        if (workspaceHost == null) {
            for (RecipeTreeOverviewScreen tree : workspaceTreeScreens.values()) {
                if (tree != this) tree.releaseEmbeddedResources();
            }
            workspaceTreeScreens.clear();
        }
        RecipeTreeClientMemory.flushPendingSave();
    }

    private void releaseEmbeddedResources() {
        pendingJeiSelection = null;
        pendingAlternativeSelection = null;
        planService.close();
    }

    @Override
    public void onClose() {
        // Embedded trees are controllers owned by the visible workspace screen, not standalone screens.
        // Closing one directly would terminate its planner while the host continued ticking it.
        if (workspaceHost != null) {
            workspaceHost.onClose();
            return;
        }
        releaseWorkspaceResources();
        this.minecraft.setScreen(returnScreen);
    }

    private void rememberSelectionForNode(RecipeTreeNodeViewModel targetNode, RecipeTreeRecipeViewModel selected) {
        RecipeTreeNodeViewModel parent = targetNode.parent();
        if (parent == null) {
            return;
        }
        for (int inputIndex = 0; inputIndex < parent.recipe().inputs().size(); inputIndex++) {
            RecipeTreeInputViewModel input = parent.recipe().inputs().get(inputIndex);
            if (input.child() != targetNode) continue;
            String signature = signatureOf(input);
            forgetManualCollapse(parent, inputIndex, input, synchronizationSignatureOf(input));
            context.rememberSelection(parent, inputIndex, input, signature, selected);
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
        if (batch.isEmpty()) {
            pendingJeiSelection = null;
            return;
        }
        String materialSignature = synchronizationSignatureOf(batch.getFirst());
        if (!applyRecipeSelectionAcrossMatchingMaterials(materialSignature, selected)) {
            // Fallback for a detached node that is no longer reachable from the active project.
            recordHistory();
            Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache = new HashMap<>();
            for (RecipeTreeNodeViewModel targetNode : batch) {
                if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                    markCycleBlocked(targetNode);
                    continue;
                }
                clearCycleBlocked(targetNode);
                rememberSelectionForNode(targetNode, selected);
                targetNode.setRecipe(selected);
                autoApplyRememberedChildren(targetNode, rememberedCache);
            }
        }
        pendingJeiSelection = null;
    }

    private void applySelectedRecipe(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            RecipeTreeRecipeViewModel selected) {
        String materialSignature = targetNode != null
                ? synchronizationSignatureOf(targetNode)
                : (targetLeaf == null ? "" : synchronizationSignatureOf(targetLeaf.representative()));
        boolean applied = applyRecipeSelectionAcrossMatchingMaterials(materialSignature, selected);
        if (!applied && targetNode != null) {
            if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                markCycleBlocked(targetNode);
                showRecursiveRecipeMessage();
            } else {
                recordHistory();
                clearCycleBlocked(targetNode);
                rememberSelectionForNode(targetNode, selected);
                targetNode.setRecipe(selected);
                autoApplyRememberedChildren(targetNode);
            }
        } else if (!applied && targetLeaf != null) {
            if (wouldCauseRecursiveLeafExpansion(targetLeaf, selected)) {
                markCycleBlocked(targetLeaf);
                showRecursiveRecipeMessage();
            } else {
                recordHistory();
                applyLeafSelection(targetLeaf, selected);
            }
        }
        pendingJeiSelection = null;
        rebuildLayout();
    }

    private boolean applyRecipeSelectionAcrossMatchingMaterials(String materialSignature,
            RecipeTreeRecipeViewModel selected) {
        if (materialSignature == null || materialSignature.isBlank()) {
            return false;
        }
        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> matches =
                collectInputsByMaterialSignature(materialSignature);
        if (matches.isEmpty()) {
            return false;
        }

        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> applicable = new LinkedHashMap<>();
        boolean blockedAny = false;
        for (Map.Entry<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> entry : matches.entrySet()) {
            if (entry.getKey().containsRecipe(selected)) {
                blockedAny = true;
                for (RecipeTreeInputViewModel input : entry.getValue()) {
                    cycleWarningsChanged |= cycleBlockedInputs.add(input);
                }
            } else {
                applicable.put(entry.getKey(), entry.getValue());
            }
        }
        if (applicable.isEmpty()) {
            if (blockedAny) showRecursiveRecipeMessage();
            return true;
        }

        recordHistory();
        Map<String, Optional<RecipeTreeRecipeViewModel>> rememberedCache = new HashMap<>();
        for (Map.Entry<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> entry : applicable.entrySet()) {
            RecipeTreeNodeViewModel owner = entry.getKey();
            RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, owner);
            for (RecipeTreeInputViewModel input : entry.getValue()) {
                cycleBlockedInputs.remove(input);
                int inputIndex = owner.recipe().inputs().indexOf(input);
                String memorySignature = signatureOf(input);
                forgetManualCollapse(owner, inputIndex, input, synchronizationSignatureOf(input));
                context.rememberSelection(owner, inputIndex, input, memorySignature, selected);
                input.setChild(childNode);
            }
            autoApplyRememberedChildren(childNode, rememberedCache);
        }
        if (blockedAny) showRecursiveRecipeMessage();
        return true;
    }

    private Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> collectInputsByMaterialSignature(
            String targetSignature) {
        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> result = new LinkedHashMap<>();
        ArrayDeque<RecipeTreeNodeViewModel> pending = new ArrayDeque<>();
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        pending.addLast(context.root());
        while (!pending.isEmpty()) {
            RecipeTreeNodeViewModel node = pending.removeLast();
            if (!visited.add(node)) continue;
            for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
                if (targetSignature.equals(synchronizationSignatureOf(input))) {
                    result.computeIfAbsent(node, ignored -> new ArrayList<>()).add(input);
                }
                if (input.child() != null) pending.addLast(input.child());
            }
        }
        return result;
    }

    private void showRecursiveRecipeMessage() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_recursive_recipe")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private void focusThisWorkspaceTree() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        host.focusedWorkspaceTree = this;
        host.workspace.activate(context);
        host.syncToggleExistingPatternButton();
        host.updateSelectionButtons();
    }

    private boolean openSelectionWithJei(RecipeTreeNodeViewModel targetNode) {
        focusThisWorkspaceTree();
        if (shouldBlockExpansion(targetNode)) {
            showExistingPatternExpansionBlockedMessage();
            return true;
        }
        ITypedIngredient<?> ingredient = targetNode.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(targetNode, null, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, workspaceHostScreen(), ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openSelectionWithJei(MergedLeaf targetLeaf) {
        focusThisWorkspaceTree();
        if (shouldBlockExpansion(targetLeaf)) {
            showExistingPatternExpansionBlockedMessage();
            return true;
        }
        ITypedIngredient<?> ingredient = getJeiSelectionIngredient(targetLeaf.representative());
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(null, targetLeaf, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, workspaceHostScreen(), ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openRecipeTargetsSelectionWithJei(List<RecipeTreeNodeViewModel> targets) {
        focusThisWorkspaceTree();
        if (targets.isEmpty()) {
            return false;
        }
        if (targets.stream().anyMatch(this::shouldBlockExpansion)) {
            showExistingPatternExpansionBlockedMessage();
            return true;
        }
        RecipeTreeNodeViewModel first = targets.getFirst();
        ITypedIngredient<?> ingredient = first.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(first, null, List.copyOf(targets));
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, workspaceHostScreen(), ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean handleMergedLayerMaterialClick(LayerMaterial material) {
        if (shouldBlockExpansion(material)) {
            showExistingPatternExpansionBlockedMessage();
            return true;
        }
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
        focusThisWorkspaceTree();
        if (pendingJeiSelection == null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.jeict.recipe_tree_select_target_first")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if ((pendingJeiSelection.targetNode() != null && shouldBlockExpansion(pendingJeiSelection.targetNode()))
                || (pendingJeiSelection.targetLeaf() != null && shouldBlockExpansion(pendingJeiSelection.targetLeaf()))
                || pendingJeiSelection.recipeApplyBatch().stream().anyMatch(this::shouldBlockExpansion)) {
            pendingJeiSelection = null;
            showExistingPatternExpansionBlockedMessage();
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
        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> byParent = new LinkedHashMap<>();
        for (int i = 0; i < target.members().size(); i++) {
            RecipeTreeInputViewModel input = target.members().get(i);
            cycleBlockedInputs.remove(input);
            RecipeTreeNodeViewModel owner = target.parentForMember(i);
            byParent.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(input);
        }
        for (Map.Entry<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> entry : byParent.entrySet()) {
            RecipeTreeNodeViewModel owner = entry.getKey();
            RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, owner);
            for (RecipeTreeInputViewModel input : entry.getValue()) {
                int inputIndex = owner.recipe().inputs().indexOf(input);
                String signature = signatureOf(input);
                forgetManualCollapse(owner, inputIndex, input, synchronizationSignatureOf(input));
                context.rememberSelection(owner, inputIndex, input, signature, selected);
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
        for (int inputIndex = 0; inputIndex < parent.recipe().inputs().size(); inputIndex++) {
            RecipeTreeInputViewModel input = parent.recipe().inputs().get(inputIndex);
            int slotIndex = inputIndex;
            String signature = signatureOf(input);
            String memoryKey = RecipeTreeMemoryKey.of(parent, slotIndex, input, signature);
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                RecipeTreeNodeViewModel canonical = expandedChildrenBySignature.get(signature);
                if (canonical != null && canonical.recipe().sameRecipeAs(child.recipe())) {
                    input.setChild(canonical);
                    child = canonical;
                } else {
                    expandedChildrenBySignature.putIfAbsent(signature, child);
                }
            } else if (!isManuallyCollapsed(parent, slotIndex, input, synchronizationSignatureOf(input))
                    && !shouldBlockExpansion(input)) {
                RecipeTreeRecipeViewModel remembered = rememberedCache
                        .computeIfAbsent(memoryKey,
                                key -> Optional.ofNullable(context.getRememberedSelection(parent, slotIndex, input,
                                        signature, input.displayIngredient())))
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
        for (int memberIndex = 0; memberIndex < leaf.members().size(); memberIndex++) {
            RecipeTreeInputViewModel input = leaf.members().get(memberIndex);
            RecipeTreeNodeViewModel parent = leaf.parentForMember(memberIndex);
            int inputIndex = parent.recipe().inputs().indexOf(input);
            if (isManuallyCollapsed(parent, inputIndex, input, synchronizationSignatureOf(input))) return true;
        }
        return false;
    }

    private boolean isManuallyCollapsed(String signature) {
        return manuallyCollapsedSignatures.contains(signature) || context.isCollapsed(signature);
    }

    private boolean isManuallyCollapsed(RecipeTreeNodeViewModel parent, int inputIndex,
            RecipeTreeInputViewModel input, String signature) {
        if (manuallyCollapsedSignatures.contains(signature)) {
            return true;
        }
        CachedCollapsedState cached = collapsedStateCache.get(input);
        if (cached != null && cached.parent() == parent && cached.inputIndex() == inputIndex
                && cached.signature().equals(signature)) {
            return cached.collapsed();
        }
        boolean collapsed = context.isCollapsed(signature);
        collapsedStateCache.put(input, new CachedCollapsedState(parent, inputIndex, signature, collapsed));
        return collapsed;
    }

    private void rememberManualCollapse(String signature) {
        manuallyCollapsedSignatures.add(signature);
        collapsedStateCache.clear();
        context.rememberCollapsed(signature);
    }

    private void rememberManualCollapse(RecipeTreeNodeViewModel parent, int inputIndex,
            RecipeTreeInputViewModel input, String signature) {
        manuallyCollapsedSignatures.add(signature);
        collapsedStateCache.clear();
        context.rememberCollapsed(signature);
    }

    private void forgetManualCollapse(String signature) {
        manuallyCollapsedSignatures.remove(signature);
        collapsedStateCache.clear();
        context.forgetCollapsed(signature);
    }

    private void forgetManualCollapse(RecipeTreeNodeViewModel parent, int inputIndex,
            RecipeTreeInputViewModel input, String signature) {
        manuallyCollapsedSignatures.remove(signature);
        collapsedStateCache.clear();
        context.forgetCollapsed(signature);
    }

    private String synchronizationSignatureOf(RecipeTreeInputViewModel input) {
        ITypedIngredient<?> ingredient = input.displayIngredient();
        if (ingredient != null) {
            return signatureOfMaterialIngredient(ingredient);
        }
        ItemStack stack = input.displayStack();
        if (!stack.isEmpty()) {
            return signatureOfItemType(stack);
        }
        return "name#" + displayNameOf(input).toLowerCase(java.util.Locale.ROOT);
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

    private String synchronizationSignatureOf(RecipeTreeNodeViewModel node) {
        ITypedIngredient<?> primaryOutput = node.recipe().primaryOutputIngredient();
        if (primaryOutput != null) {
            return signatureOfMaterialIngredient(primaryOutput);
        }
        ItemStack output = node.recipe().primaryOutput();
        if (!output.isEmpty()) {
            return signatureOfItemType(output);
        }
        return "node#" + node.recipe().title().getString().toLowerCase(java.util.Locale.ROOT);
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
        for (int inputIndex = 0; inputIndex < parent.recipe().inputs().size(); inputIndex++) {
            RecipeTreeInputViewModel input = parent.recipe().inputs().get(inputIndex);
            if (input.child() != node) continue;
            String signature = synchronizationSignatureOf(input);
            recordHistory();
            rememberManualCollapse(parent, inputIndex, input, signature);
            collapseExpandedInputsBySignature(signature);
            return;
        }
    }

    private void collapseLayerMaterial(LayerMaterial material) {
        Set<String> signatures = new java.util.LinkedHashSet<>();
        boolean historyRecorded = false;
        for (RecipeTreeNodeViewModel node : material.recipeTargets()) {
            RecipeTreeNodeViewModel parent = node.parent();
            if (parent == null) continue;
            for (int inputIndex = 0; inputIndex < parent.recipe().inputs().size(); inputIndex++) {
                RecipeTreeInputViewModel input = parent.recipe().inputs().get(inputIndex);
                if (input.child() != node) continue;
                if (!historyRecorded) {
                    recordHistory();
                    historyRecorded = true;
                }
                String signature = synchronizationSignatureOf(input);
                rememberManualCollapse(parent, inputIndex, input, signature);
                if (signature != null && !signature.isBlank()) signatures.add(signature);
            }
        }
        for (String signature : signatures) collapseExpandedInputsBySignature(signature);
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
                if (targetSignature.equals(synchronizationSignatureOf(input))) {
                    input.setChild(null);
                } else {
                    pending.addLast(child);
                }
            }
        }
    }

    private void jumpToMaterial(RequestedIngredient material) {
        UnresolvedInputSlot slot = firstUnresolvedSlot(material);
        jumpToMaterialSignature(slot == null ? signatureOf(material) : signatureOf(slot.input()));
    }

    private void jumpToMaterialSignature(String targetSignature) {
        if (autoMergeSameMaterials) {
            jumpToMaterialMergedLayers(targetSignature);
            return;
        }
        PositionedNode matchedLeaf = null;
        double[] localCenter = currentLocalViewportCenter();
        double bestDistance = Double.MAX_VALUE;
        for (PositionedNode node : positionedNodes) {
            MergedLeaf leaf = node.graph().mergedLeaf();
            if (leaf == null || !signatureOf(leaf.representative()).equals(targetSignature)) {
                continue;
            }
            double centerX = node.x() + node.graph().width() / 2.0D;
            double centerY = node.y() + NODE_HEIGHT / 2.0D;
            double distance = Math.pow(centerX - localCenter[0], 2) + Math.pow(centerY - localCenter[1], 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                matchedLeaf = node;
            }
        }
        if (matchedLeaf == null) return;
        centerWorkspaceLocalPoint(matchedLeaf.x() + matchedLeaf.graph().width() / 2.0D,
                matchedLeaf.y() + NODE_HEIGHT / 2.0D);
    }

    private boolean layerMaterialMatchesSignature(LayerMaterial mat, String targetSignature) {
        for (RecipeTreeInputViewModel input : mat.leafInputs()) {
            if (signatureOf(input).equals(targetSignature)) return true;
        }
        for (RecipeTreeNodeViewModel node : mat.recipeTargets()) {
            if (signatureOf(node).equals(targetSignature)) return true;
        }
        return false;
    }

    private void jumpToMaterialMergedLayers(String targetSignature) {
        double[] localCenter = currentLocalViewportCenter();
        double matchedX = 0.0D;
        double matchedY = 0.0D;
        double bestDistance = Double.MAX_VALUE;
        int startX = 36;
        int baseY = 42 + TOP_MATERIALS_OFFSET;
        for (int depth = 0; depth < mergedLayerRows.size(); depth++) {
            LayerRow row = mergedLayerRows.get(depth);
            LayerRowRenderCache cache = mergedRowRenderCaches.get(row);
            if (cache == null) continue;
            int rowX = mergedRowX(startX, cache);
            for (int index = 0; index < row.materials().size(); index++) {
                LayerMaterial material = row.materials().get(index);
                if (!layerMaterialMatchesSignature(material, targetSignature)) continue;
                double centerX = rowX + cache.centerOffsets()[index];
                double centerY = baseY + depth * LEVEL_GAP + NODE_HEIGHT / 2.0D;
                double distance = Math.pow(centerX - localCenter[0], 2) + Math.pow(centerY - localCenter[1], 2);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    matchedX = centerX;
                    matchedY = centerY;
                }
            }
        }
        if (bestDistance < Double.MAX_VALUE) centerWorkspaceLocalPoint(matchedX, matchedY);
    }

    private double[] currentLocalViewportCenter() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        WorkspaceTreePlacement placement = host.workspaceTreePlacements().stream()
                .filter(candidate -> candidate.screen() == this)
                .findFirst().orElse(null);
        double screenX = (host.canvasLeft() + host.canvasRight()) * 0.5D;
        double screenY = (HEADER_HEIGHT + host.height - host.currentFooterHeight()) * 0.5D;
        double workspaceX = (screenX - host.panX) / host.zoom;
        double workspaceY = (screenY - host.panY) / host.zoom;
        return placement == null
                ? new double[] { workspaceX, workspaceY }
                : new double[] { workspaceX - placement.offsetX(), workspaceY - placement.offsetY() };
    }

    private void centerWorkspaceLocalPoint(double localX, double localY) {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        WorkspaceTreePlacement placement = host.workspaceTreePlacements().stream()
                .filter(candidate -> candidate.screen() == this)
                .findFirst().orElse(null);
        double workspaceX = localX + (placement == null ? 0.0D : placement.offsetX());
        double workspaceY = localY + (placement == null ? 0.0D : placement.offsetY());
        double screenX = (host.canvasLeft() + host.canvasRight()) * 0.5D;
        double screenY = HEADER_HEIGHT
                + (host.height - host.currentFooterHeight() - HEADER_HEIGHT) * 0.45D;
        host.panX = screenX - workspaceX * host.zoom;
        host.panY = screenY - workspaceY * host.zoom;
        host.initializedPan = true;
        host.initializedWorkspaceView = true;
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
        strategyButton.visible = true;
        strategyButton.active = true;
        toggleExistingPatternButton.visible = backend != null && backend.supportsExistingPatternHints();
        toggleExistingPatternButton.active = toggleExistingPatternButton.visible;

        int settingsX = 8;
        int settingsWidth = settingsOpen ? SETTINGS_WIDTH - 14 : 24;
        Button[] settingsButtons = {
                autoMergeButton, computeQuantitiesButton, autoUniqueRecipeButton, memoryReadingButton,
                strategyButton, toggleExistingPatternButton, styleButton
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

        int headerActionsRight = this.width - 160;
        planReportButton.setWidth(Math.max(48, this.font.width(planReportButton.getMessage()) + 12));
        projectsButton.setWidth(Math.max(54, this.font.width(projectsButton.getMessage()) + 12));
        redoButton.setWidth(24);
        undoButton.setWidth(24);
        for (Button button : new Button[] { planReportButton, projectsButton, redoButton, undoButton }) {
            headerActionsRight -= button.getWidth();
            button.setX(headerActionsRight);
            button.setY(8);
            button.setHeight(20);
            headerActionsRight -= 4;
        }
        int searchX = 64;
        int titleReserve = this.width >= 900 ? 260 : 150;
        int availableBeforeActions = Math.max(searchX + 96, headerActionsRight - 8);
        int searchWidth = Math.min(420, Math.max(96, availableBeforeActions - searchX - titleReserve));
        searchBox.setX(searchX);
        searchBox.setY(7);
        searchBox.setWidth(searchWidth);
        updateSearchHint();
        headerTitleLeft = searchBox.getX() + searchBox.getWidth() + 8;
        headerTitleRight = Math.max(headerTitleLeft, headerActionsRight - 8);
        projectsButton.visible = true;
        planReportButton.visible = true;
        undoButton.visible = true;
        redoButton.visible = true;

        boolean supportsSubstitution = backend != null && backend.supportsSubstitution();
        itemSubstitutionButton.visible = supportsSubstitution;
        itemSubstitutionButton.active = supportsSubstitution;
        fluidSubstitutionButton.visible = supportsSubstitution;
        fluidSubstitutionButton.active = supportsSubstitution;
        encodeButton.visible = backend != null && backend.supportsEncode();
        encodeButton.active = encodeButton.visible;
        uploadButton.visible = backend != null;
        uploadButton.active = backend != null && backend.supportsUpload();
        itemSubstitutionButton.setWidth(Math.max(72, this.font.width(itemSubstitutionButton.getMessage()) + 18));
        fluidSubstitutionButton.setWidth(Math.max(72, this.font.width(fluidSubstitutionButton.getMessage()) + 18));
        encodeButton.setWidth(Math.max(72, this.font.width(encodeButton.getMessage()) + 14));
        uploadButton.setWidth(Math.max(54, this.font.width(uploadButton.getMessage()) + 14));
        itemSubstitutionButton.setHeight(20);
        fluidSubstitutionButton.setHeight(20);
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
            actionsRight = encodeButton.getX() - 5;
        }
        if (fluidSubstitutionButton.visible) {
            fluidSubstitutionButton.setX(actionsRight - fluidSubstitutionButton.getWidth());
            fluidSubstitutionButton.setY(this.height - 27);
            actionsRight = fluidSubstitutionButton.getX() - 5;
        }
        if (itemSubstitutionButton.visible) {
            itemSubstitutionButton.setX(actionsRight - itemSubstitutionButton.getWidth());
            itemSubstitutionButton.setY(this.height - 27);
        }
        syncComputeQuantitiesButton();
        syncStyleButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncMemoryReadingButton();
        syncAutoMergeButton();
        syncStrategyButton();
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
                    panelX + 24, optionY + 5,
                    selected ? theme.onControlText() : theme.alternativeText(), false);
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
        recordHistory();
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
        List<FloatingMaterialOverlayState.Entry> entries = new ArrayList<>();
        refreshTopMaterialRenderCacheIfNeeded();
        for (TopMaterialRenderData data : topMaterialRenderData) {
            UnresolvedInputSlot slot = firstUnresolvedSlot(data.material());
            entries.add(new FloatingMaterialOverlayState.Entry(data.stack(), null,
                    data.displayCount(), data.label(), machineIcon(slot), machineName(slot), machineKey(slot)));
        }
        for (GenericTopMaterialRenderData data : genericTopMaterialRenderData) {
            MergedLeaf leaf = data.leaf();
            RecipeTreeNodeViewModel parent = leaf.parentNode();
            entries.add(new FloatingMaterialOverlayState.Entry(ItemStack.EMPTY, leaf.ingredient(),
                    leaf.totalAmount(), data.label(), parent.recipe().subtitleIcon(), parent.recipe().subtitle(),
                    machineKey(parent)));
        }
        return new FloatingMaterialOverlayState.Snapshot(entries, context);
    }

    private @Nullable UnresolvedInputSlot firstUnresolvedSlot(RequestedIngredient material) {
        List<UnresolvedInputSlot> slots = unresolvedInputsBySignature.get(signatureOf(material));
        return slots == null || slots.isEmpty() ? null : slots.getFirst();
    }

    private static @Nullable IDrawable machineIcon(@Nullable UnresolvedInputSlot slot) {
        return slot == null ? null : slot.parentNode().recipe().subtitleIcon();
    }

    private static @Nullable Component machineName(@Nullable UnresolvedInputSlot slot) {
        return slot == null ? null : slot.parentNode().recipe().subtitle();
    }

    private static String machineKey(@Nullable UnresolvedInputSlot slot) {
        return slot == null ? "" : machineKey(slot.parentNode());
    }

    private static String machineKey(RecipeTreeNodeViewModel parent) {
        Component name = parent.recipe().subtitle();
        return name == null ? "" : name.toString();
    }

    private <T> void renderJeiIngredientTyped(GuiGraphics graphics, IIngredientManager ingredientManager, ITypedIngredient<?> ingredient,
            int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        T displayIngredient = typed.getIngredient();
        if (displayIngredient instanceof FluidStack fluidStack) {
            FluidStack fullIconStack = fluidStack.copy();
            // JEI's fluid renderer uses the amount as a fill ratio; quantities must not shrink the icon.
            fullIconStack.setAmount(Integer.MAX_VALUE);
            @SuppressWarnings("unchecked")
            T normalized = (T) fullIconStack;
            displayIngredient = normalized;
        } else {
            Object fullChemical = GenericIngredientUtil.tryCopyMekanismChemicalForIcon(displayIngredient);
            if (fullChemical != null) {
                @SuppressWarnings("unchecked")
                T normalized = (T) fullChemical;
                displayIngredient = normalized;
            }
        }
        int rendererWidth = Math.max(1, renderer.getWidth());
        int rendererHeight = Math.max(1, renderer.getHeight());
        float scale = Math.min(16.0F / rendererWidth, 16.0F / rendererHeight);
        float offsetX = (16.0F - rendererWidth * scale) * 0.5F;
        float offsetY = (16.0F - rendererHeight * scale) * 0.5F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + offsetX, y + offsetY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderer.render(graphics, displayIngredient, 0, 0);
        graphics.pose().popPose();
    }

    private void updateSearchHint() {
        if (searchBox == null) return;
        String fullHint = Component.translatable("gui.jeict.recipe_tree.search_hint").getString();
        int maxWidth = Math.max(12, searchBox.getWidth() - 12);
        if (this.font.width(fullHint) <= maxWidth) {
            searchBox.setHint(Component.literal(fullHint));
            return;
        }
        String ellipsis = "…";
        String clipped = this.font.plainSubstrByWidth(fullHint,
                Math.max(1, maxWidth - this.font.width(ellipsis)));
        searchBox.setHint(Component.literal(clipped + ellipsis));
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

    private void toggleExistingPatternExpansion() {
        context.setDisableExistingPatternExpansion(!context.disableExistingPatternExpansion());
        if (context.disableExistingPatternExpansion()) {
            recordHistory();
            if (collapseExpandedExistingPatternNodes(context.root())) {
                closeSelection();
            }
        }
        syncToggleExistingPatternButton();
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) host.syncToggleExistingPatternButton();
        rebuildLayout();
    }

    private void syncToggleExistingPatternButton() {
        if (!settingsOpen) {
            toggleExistingPatternButton.setMessage(Component.empty());
            return;
        }
        RecipeTreeOverviewScreen target = workspaceHost == null ? focusedTree() : this;
        toggleExistingPatternButton.setMessage(Component.translatable(target.context.disableExistingPatternExpansion()
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
        autoMergeButton.setMessage(focusedTree().autoMergeSameMaterials
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

    private List<RecipeTreeOverviewScreen> workspaceTreesInOrder() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) return host.workspaceTreesInOrder();
        syncWorkspaceTreeScreens();
        List<RecipeTreeOverviewScreen> trees = new ArrayList<>(workspace.size());
        for (RecipeTreeRootContext treeContext : workspace.trees().values()) {
            RecipeTreeOverviewScreen tree = screenFor(treeContext);
            if (tree != null) trees.add(tree);
        }
        return List.copyOf(trees);
    }

    private void uploadWorkspacePatterns() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) {
            host.uploadWorkspacePatterns();
            return;
        }
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsUpload()) return;
        if (backend.supportsEditablePatternDrafts()) {
            List<PatternEncodingRequest> requests = collectWorkspacePatternRequests(backend);
            if (requests.isEmpty()) {
                showNoPatternsMessage();
                return;
            }
            if (!validatePatternRequests(backend, requests)) return;
            if (backend.uploadPatternDrafts(requests)) onClose();
            return;
        }
        List<RecipeTreeRecipeViewModel> recipes = collectWorkspaceEncodableRecipes();
        if (recipes.isEmpty()) {
            showNoPatternsMessage();
            return;
        }
        if (backend.uploadPatterns(recipes)) onClose();
    }

    private void encodeWorkspacePatterns() {
        RecipeTreeOverviewScreen host = workspaceHostScreen();
        if (host != this) {
            host.encodeWorkspacePatterns();
            return;
        }
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        if (backend == null || !backend.supportsEncode()) return;
        if (backend.supportsEditablePatternDrafts()) {
            List<PatternEncodingRequest> requests = collectWorkspacePatternRequests(backend);
            if (requests.isEmpty()) {
                showNoPatternsMessage();
                return;
            }
            if (!validatePatternRequests(backend, requests)) return;
            if (backend.encodePatternDrafts(requests)) onClose();
            return;
        }
        List<RecipeTreeRecipeViewModel> recipes = collectWorkspaceEncodableRecipes();
        if (recipes.isEmpty()) {
            showNoPatternsMessage();
            return;
        }
        if (backend.encodePatterns(recipes)) onClose();
    }

    private List<PatternEncodingRequest> collectWorkspacePatternRequests(CraftingTreeBackend backend) {
        Map<String, PatternEncodingRequest> unique = new LinkedHashMap<>();
        for (RecipeTreeOverviewScreen tree : workspaceTreesInOrder()) {
            for (PatternEncodingRequest request : tree.collectEncodablePatternRequests(backend)) {
                unique.putIfAbsent(request.draft().fingerprint(), request);
            }
        }
        return List.copyOf(unique.values());
    }

    private List<RecipeTreeRecipeViewModel> collectWorkspaceEncodableRecipes() {
        Map<String, RecipeTreeRecipeViewModel> unique = new LinkedHashMap<>();
        for (RecipeTreeOverviewScreen tree : workspaceTreesInOrder()) {
            for (RecipeTreeRecipeViewModel recipe : tree.collectEncodableRecipes()) {
                unique.putIfAbsent(recipe.stableIdentity(), recipe);
            }
        }
        return List.copyOf(unique.values());
    }

    private void showScreenNotice(List<Component> lines, boolean error) {
        List<Component> visible = lines == null ? List.of() : lines.stream()
                .filter(java.util.Objects::nonNull)
                .limit(4)
                .map(line -> (Component) line.copy())
                .toList();
        if (visible.isEmpty()) return;
        screenNotice = new ScreenNotice(visible, error, System.currentTimeMillis() + SCREEN_NOTICE_DURATION_MS);
    }

    private void renderScreenNotice(GuiGraphics graphics, RecipeTreeTheme.Palette theme) {
        ScreenNotice notice = screenNotice;
        if (notice == null) return;
        if (System.currentTimeMillis() >= notice.expiresAtMillis()) {
            screenNotice = null;
            return;
        }
        int maxTextWidth = Math.min(360, Math.max(120, canvasRight() - canvasLeft() - 32));
        List<Component> lines = notice.lines().stream().map(line -> trimToWidth(line, maxTextWidth)).toList();
        int contentWidth = lines.stream().mapToInt(this.font::width).max().orElse(80);
        int boxWidth = Math.min(maxTextWidth + 12, Math.max(132, contentWidth + 12));
        int boxHeight = lines.size() * 11 + 10;
        int x = canvasLeft() + Math.max(8, (canvasRight() - canvasLeft() - boxWidth) / 2);
        int y = this.height - currentFooterHeight() - boxHeight - 8;
        int border = notice.error() ? theme.danger() : theme.partial();
        graphics.fill(x, y, x + boxWidth, y + boxHeight, theme.noticeBackground());
        RecipeTreeTheme.drawBorder(graphics, x, y, x + boxWidth, y + boxHeight, border);
        int textY = y + 5;
        for (Component line : lines) {
            graphics.drawString(this.font, line, x + 6, textY, notice.error() ? theme.danger() : theme.titleText(), false);
            textY += 11;
        }
    }

    private void showNoPatternsMessage() {
        showScreenNotice(List.of(Component.translatable("message.jeict.recipe_tree_no_patterns")), false);
    }

    private List<PatternEncodingRequest> collectEncodablePatternRequests(CraftingTreeBackend backend) {
        Map<String, PatternEncodingRequest> unique = new LinkedHashMap<>();
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (recipe.primaryOutputIngredient() == null) continue;
            PatternEncodingDraft draft = patternDraftFor(recipe);
            PatternEncodingRequest request = new PatternEncodingRequest(recipe, draft);
            if (!backend.hasExactPatternDraft(request)) unique.putIfAbsent(draft.fingerprint(), request);
        }
        return List.copyOf(unique.values());
    }

    private boolean validatePatternRequests(CraftingTreeBackend backend, List<PatternEncodingRequest> requests) {
        for (PatternEncodingRequest request : requests) {
            if (!isPatternDraftValid(request.draft())) {
                showScreenNotice(List.of(
                        Component.translatable("message.jeict.recipe_tree_pattern_invalid", request.draft().patternName()),
                        Component.translatable("gui.jeict.recipe_tree.pattern_invalid")), true);
                return false;
            }
            List<Component> errors = backend.validatePatternDraft(request);
            if (!errors.isEmpty()) {
                List<Component> notice = new ArrayList<>(Math.min(4, errors.size() + 1));
                notice.add(Component.translatable("message.jeict.recipe_tree_pattern_invalid",
                        request.draft().patternName()));
                errors.stream().limit(3).forEach(notice::add);
                showScreenNotice(notice, true);
                return false;
            }
        }
        screenNotice = null;
        return true;
    }

    private List<RecipeTreeRecipeViewModel> collectEncodableRecipes() {
        List<RecipeTreeRecipeViewModel> recipes = new ArrayList<>();
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (!hasExistingPatternForOutput(recipe) && recipe.primaryOutputIngredient() != null) recipes.add(recipe);
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
                ignored -> backend.hasExactPattern(recipe));
    }

    private boolean collapseExpandedExistingPatternNodes(RecipeTreeNodeViewModel root) {
        Set<RecipeTreeNodeViewModel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return collapseExpandedExistingPatternNodes(root, visited);
    }

    private boolean collapseExpandedExistingPatternNodes(RecipeTreeNodeViewModel parent,
            Set<RecipeTreeNodeViewModel> visited) {
        if (!visited.add(parent)) {
            return false;
        }
        boolean changed = false;
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                continue;
            }
            if (hasExistingPatternForOutput(child.recipe())) {
                context.forgetSelection(signatureOf(input));
                input.setChild(null);
                changed = true;
                continue;
            }
            changed |= collapseExpandedExistingPatternNodes(child, visited);
        }
        return changed;
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
        return formatCompactCount((long) count);
    }

    private static String formatCompactCount(long count) {
        if (count < 1000L) {
            return Long.toString(count);
        }
        double value = count;
        String[] suffixes = { "K", "M", "B" };
        int suffixIndex = -1;
        while (value >= 1000.0D && suffixIndex + 1 < suffixes.length) {
            value /= 1000.0D;
            suffixIndex++;
        }
        if (suffixIndex < 0) {
            return Long.toString(count);
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
            boolean cycleWarning, List<GraphNode> children, int width) {
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

    private enum PatternControl {
        CLEAR,
        CYCLE_OUTPUT,
        ITEM_SUBSTITUTION,
        FLUID_SUBSTITUTION,
        RESET,
        PRESERVE_ORDER,
        SCROLLBAR
    }

    private record ScreenNotice(List<Component> lines, boolean error, long expiresAtMillis) {
    }

    private record PatternDraftSlotBounds(boolean input, int index, @Nullable PatternEncodingSlot slot,
            int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record PatternControlBounds(PatternControl control, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
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
            String label, int width, ItemStack stack, boolean showsPatternHint, boolean cycleWarning) {
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

    private record GenericTopMaterialRenderData(MergedLeaf leaf, String label, int width, boolean cycleWarning) {
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
    private record TreeLogicalBounds(double minX, double maxX, double minY, double maxY) {
        double width() { return Math.max(1.0D, maxX - minX); }
        double height() { return Math.max(1.0D, maxY - minY); }
        double centerX() { return (minX + maxX) * 0.5D; }
        double centerY() { return (minY + maxY) * 0.5D; }
        TreeLogicalBounds translated(double x, double y) { return new TreeLogicalBounds(minX + x, maxX + x, minY + y, maxY + y); }
        TreeLogicalBounds expanded(double x, double y) { return new TreeLogicalBounds(minX - x, maxX + x, minY - y, maxY + y); }
        boolean contains(double x, double y) { return x >= minX && x <= maxX && y >= minY && y <= maxY; }
    }

    private record WorkspaceTreePlacement(GridPosition position, RecipeTreeOverviewScreen screen,
            double offsetX, double offsetY, TreeLogicalBounds bounds) {
    }

    private record WorkspaceLinkBounds(GridPosition origin, Direction direction, double x, double y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

}
