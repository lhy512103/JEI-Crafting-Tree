package com.lhy.jeict.client.screen;

import com.lhy.jeict.integration.JeiRuntimeAccess;
import com.lhy.jeict.tree.IngredientKey;
import com.lhy.jeict.tree.RecipeGraphCache;
import com.lhy.jeict.tree.RecipeNode;
import com.lhy.jeict.tree.RecipeSelectionMemory;
import com.lhy.jeict.tree.RecipeTree;
import com.lhy.jeict.tree.TreeNode;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.lhy.jeict.client.screen.layout.LayoutNode;
import com.lhy.jeict.client.screen.layout.RecipeTreeLayout;
import net.minecraft.world.item.crafting.Recipe;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecipeTreeScreen extends AbstractContainerScreen<RecipeTreeScreen.ContainerTransfer> {
    private static final int NODE_PADDING = 6;
    private static final int ITEM_ICON_SIZE = 16;
    private static final int RECIPE_ICON_SIZE = 18;
    private static final int NODE_ICON_GAP = 5;
    private static final int NODE_HEIGHT = 30;
    private static final int SIBLING_GAP = 18;
    private static final int LEVEL_GAP = 76;
    private static final int SCREEN_BG = 0xFFD1D6DA;
    private static final int LINE_COLOR = 0xFF9CA8AE;
    private static final int COST_ROW_HEIGHT = 24;
    private static final int COST_CELL_GAP = 4;
    private static final int COST_TOTAL_BORDER = 0xFF76B16F;
    private static final int COST_LEFTOVER_BORDER = 0xFFF08A53;
    private static final int NODE_BORDER = 0xE6FFFFFF;
    private static final int NODE_BORDER_HOVER = 0xFFFFFFFF;
    private static final int NODE_BORDER_SELECTED = 0xFF0D6F86;
    private static final int INVENTORY_ENOUGH_BORDER = COST_TOTAL_BORDER;
    private static final int INVENTORY_PARTIAL_BORDER = COST_LEFTOVER_BORDER;
    private static final int INVENTORY_MISSING_BORDER = 0xFFD46D63;
    private static final int INVENTORY_PANEL_BG = 0xF4E8ECEF;
    private static final int INVENTORY_PANEL_BORDER = 0xFF6B777C;
    private static final int INVENTORY_PANEL_MIN_WIDTH = 136;
    private static final int INVENTORY_PANEL_ROW_HEIGHT = 20;
    private static final int INVENTORY_PANEL_HEADER_HEIGHT = 14;
    private static final int INVENTORY_PANEL_FILTER_HEIGHT = 15;
    private static final int INVENTORY_PANEL_PADDING = 5;
    private static final int INVENTORY_PANEL_CONTROL_SIZE = 12;
    private static final int INVENTORY_PANEL_MAX_ROWS = 8;
    private static final double INVENTORY_TEXT_SCALE = 7.0 / 9.0;

    private final RecipeTree tree;
    private final List<LayoutNode> layoutNodes = new ArrayList<>();
    /**
     * 路径 7：layoutNodes 命中查找原先是 O(N) 线性扫，频繁命中测试开销随节点数线性增长。
     * 这里并行维护一份 {@link TreeNode -> LayoutNode} 索引，layoutFor 改为 O(1) 查表。
     */
    private final IdentityHashMap<TreeNode, LayoutNode> layoutNodeMap = new IdentityHashMap<>();
    /**
     * 路径 5：同一次 rebuildLayout 内的 measure 记忆化缓存，避免重复递归。
     * 每次 rebuildLayout 入口处 clear，跨帧不复用（zoom/树结构变化都需重算）。
     */
    private final IdentityHashMap<TreeNode, Integer> measureCache = new IdentityHashMap<>();
    /**
     * 路径 6：layout 重建脏标记。仅当 zoom / pan / 屏幕 size 变化或树结构变化时置 true，
     * render 主流程据此跳过同布局帧的无谓重建。
     */
    private boolean layoutDirty = true;
    private double lastZoom = Double.NaN;
    private int lastPanX = Integer.MIN_VALUE;
    private int lastPanY = Integer.MIN_VALUE;
    private int lastWidth = Integer.MIN_VALUE;
    private int lastHeight = Integer.MIN_VALUE;
    private final List<CostLayout> costLayouts = new ArrayList<>();
    private final List<InventoryCompareEntry> inventoryCompareEntries = new ArrayList<>();
    private final List<InventoryCompareLayout> inventoryCompareLayouts = new ArrayList<>();
    private final List<FilterLayout> inventoryFilterLayouts = new ArrayList<>();
    private final Map<ResourceLocation, Optional<IDrawable>> recipeIconCache = newLruCache(64);
    private final Map<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipePreviewCache = newLruCache(32);
    private double zoom = 1.0;
    private int panX;
    private int panY;
    private TreeNode selected;
    private boolean dragging;
    private int lastMouseX;
    private int lastMouseY;
    private String batchInput = "";
    private TreeNode dropdownNode;
    private ButtonLayout inventoryCompareButton;
    private ButtonLayout inventoryCloseButton;
    private ButtonLayout inventoryPinButton;
    private boolean inventoryCompareOpen;
    private boolean inventoryComparePinned;
    private InventoryFilter inventoryFilter = InventoryFilter.ALL;
    private int inventoryPanelX = -1;
    private int inventoryPanelY = -1;
    private boolean inventoryPanelDragging;
    private int inventoryPanelDragOffsetX;
    private int inventoryPanelDragOffsetY;
    private int inventoryScrollOffset;

    public RecipeTreeScreen(RecipeTree tree) {
        this(new ContainerTransfer(), Minecraft.getInstance().player.getInventory(), tree);
    }

    private RecipeTreeScreen(ContainerTransfer menu, Inventory inventory, RecipeTree tree) {
        super(menu, inventory, Component.translatable("screen.jeict.recipe_tree"));
        this.tree = tree;
        this.selected = tree.root();
        this.imageWidth = 0;
        this.imageHeight = 0;
        menu.setScreen(this);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = 0;
        this.topPos = 0;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 68, height - 26, 58, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, SCREEN_BG);
        renderHelp(graphics);
        // 路径 6：检测 zoom / pan / 屏幕尺寸是否变化；任一变化或显式 dirty 即重建 layout。
        if (layoutDirty
                || zoom != lastZoom
                || panX != lastPanX
                || panY != lastPanY
                || width != lastWidth
                || height != lastHeight) {
            rebuildLayout();
            layoutDirty = false;
            lastZoom = zoom;
            lastPanX = panX;
            lastPanY = panY;
            lastWidth = width;
            lastHeight = height;
        }
        costLayouts.clear();
        inventoryCompareEntries.clear();
        inventoryCompareLayouts.clear();
        inventoryFilterLayouts.clear();
        inventoryCompareButton = null;
        inventoryCloseButton = null;
        inventoryPinButton = null;
        renderTotals(graphics, mouseX, mouseY);
        renderBranches(graphics);
        renderNodes(graphics, mouseX, mouseY);
        renderInventoryComparePanel(graphics, mouseX, mouseY);
        renderNodeTooltip(graphics, mouseX, mouseY);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderHelp(GuiGraphics graphics) {
        int x = 10;
        int y = 8;
        graphics.drawString(font, title, x, y, 0xFF2E3D42, false);
        y += font.lineHeight + 4;
        graphics.drawString(font, Component.translatable("screen.jeict.help.left_click"), x, y, 0xFF3F4C51, false);
        y += font.lineHeight + 2;
        graphics.drawString(font, Component.translatable("screen.jeict.help.right_click"), x, y, 0xFF3F4C51, false);
        y += font.lineHeight + 2;
        graphics.drawString(font, Component.translatable("screen.jeict.help.scroll_batch"), x, y, 0xFF3F4C51, false);
    }

    private void rebuildLayout() {
        layoutNodes.clear();
        // 路径 5：measure/layout 在同一子树上会反复递归，每帧复杂度近似 O(N²)。
        // 这里用 IdentityHashMap 在单次 rebuild 内做记忆化，等价于把 measure/layout 都降到 O(N)。
        measureCache.clear();
        layoutNodeMap.clear();
        int treeWidth = measure(tree.root());
        int startX = width / 2 - scale(treeWidth) / 2 + panX;
        layout(tree.root(), startX, 140 + panY, treeWidth);
    }

    private int measure(TreeNode node) {
        Integer cached = measureCache.get(node);
        if (cached != null) {
            return cached;
        }
        List<TreeNode> children = visibleChildren(node);
        int result;
        if (children.isEmpty()) {
            result = nodeWidth(node);
        } else {
            int childrenWidth = 0;
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    childrenWidth += SIBLING_GAP;
                }
                childrenWidth += measure(children.get(i));
            }
            result = Math.max(nodeWidth(node), childrenWidth);
        }
        measureCache.put(node, result);
        return result;
    }

    private void layout(TreeNode node, int left, int y, int subtreeWidth) {
        int scaledSubtree = scale(subtreeWidth);
        int nodeW = scale(nodeWidth(node));
        int nodeH = scale(NODE_HEIGHT);
        int itemCenterX = left + scaledSubtree / 2;
        int x = itemCenterX - itemCenterOffset(node);
        LayoutNode layoutNode = new LayoutNode(node, x, y, nodeW, nodeH, itemCenterX, scale(ITEM_ICON_SIZE));
        layoutNodes.add(layoutNode);
        layoutNodeMap.put(node, layoutNode);
        List<TreeNode> children = visibleChildren(node);
        int childLeft = left + (scaledSubtree - scale(childrenWidth(children))) / 2;
        for (TreeNode child : children) {
            int childWidth = measure(child);
            layout(child, childLeft, y + scale(LEVEL_GAP), childWidth);
            childLeft += scale(childWidth + SIBLING_GAP);
        }
    }

    private int childrenWidth(List<TreeNode> children) {
        int width = 0;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                width += SIBLING_GAP;
            }
            width += measure(children.get(i));
        }
        return width;
    }

    private int nodeWidth(TreeNode node) {
        int contentWidth = ITEM_ICON_SIZE;
        if (node.recipe() != null) {
            contentWidth += RECIPE_ICON_SIZE + NODE_ICON_GAP;
        }
        return NODE_PADDING * 2 + contentWidth;
    }

    private int itemCenterOffset(TreeNode node) {
        int itemLeft = scale(NODE_PADDING);
        if (node.recipe() != null) {
            itemLeft += scale(RECIPE_ICON_SIZE + NODE_ICON_GAP);
        }
        return itemLeft + scale(ITEM_ICON_SIZE) / 2;
    }

    private List<TreeNode> visibleChildren(TreeNode node) {
        return node.expanded() ? node.children() : List.of();
    }

    private void renderBranches(GuiGraphics graphics) {
        for (LayoutNode parent : layoutNodes) {
            List<TreeNode> children = visibleChildren(parent.node());
            if (children.isEmpty()) {
                continue;
            }
            int parentX = parent.itemCenterX();
            int parentBottom = parent.y() + scale(NODE_HEIGHT);
            int junctionY = parentBottom + scale(NODE_HEIGHT) / 3;

            if (children.size() == 1) {
                LayoutNode childLayout = layoutFor(children.get(0));
                if (childLayout != null) {
                    drawVLine(graphics, parentX, parentBottom, childLayout.y());
                    renderBatchOnLine(graphics, parent.node(), parentX, parentBottom, junctionY);
                }
                continue;
            }

            drawVLine(graphics, parentX, parentBottom, junctionY);
            renderBatchOnLine(graphics, parent.node(), parentX, parentBottom, junctionY);

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (TreeNode child : children) {
                LayoutNode childLayout = layoutFor(child);
                if (childLayout != null) {
                    minX = Math.min(minX, childLayout.itemCenterX());
                    maxX = Math.max(maxX, childLayout.itemCenterX());
                }
            }
            if (minX <= maxX) {
                drawHLine(graphics, minX, maxX, junctionY);
            }
            for (TreeNode child : children) {
                LayoutNode childLayout = layoutFor(child);
                if (childLayout != null) {
                    drawVLine(graphics, childLayout.itemCenterX(), junctionY, childLayout.y());
                }
            }
        }
    }

    private void renderBatchOnLine(GuiGraphics graphics, TreeNode node, int centerX, int y1, int y2) {
        if (!canEditBatches(node)) {
            return;
        }
        String batch = "x" + node.batches();
        if (node == selected && !batchInput.isEmpty()) {
            batch = "x" + batchInput + "_";
        }
        int textW = scaledTextWidth(batch);
        int textH = scaledFontHeight();
        int labelX = centerX - textW / 2;
        int labelY = (y1 + y2 - textH) / 2;
        graphics.fill(labelX - scale(3), labelY - scale(1), labelX + textW + scale(3), labelY + textH + scale(1), 0xDDE8ECEF);
        drawScaledString(graphics, batch, labelX, labelY, 0xFF4E5B60, false);
    }

    private LayoutNode layoutFor(TreeNode node) {
        // 路径 7：命中查找从 O(N) 线性扫描改为 O(1) 查表。
        return layoutNodeMap.get(node);
    }

    private void renderNodes(GuiGraphics graphics, int mouseX, int mouseY) {
        for (LayoutNode layout : layoutNodes) {
            TreeNode node = layout.node();
            boolean hovered = layout.contains(mouseX, mouseY);
            boolean isSelected = node == selected;
            int nw = layout.width();
            int nh = layout.height();

            int fill = nodeFill(node);
            int shadow = Math.max(1, scale(2));
            graphics.fill(layout.x() + shadow, layout.y() + shadow, layout.x() + nw + shadow, layout.y() + nh + shadow, 0x22000000);
            int border = isSelected ? NODE_BORDER_SELECTED : hovered ? NODE_BORDER_HOVER : NODE_BORDER;
            drawBox(graphics, layout.x(), layout.y(), nw, nh, fill, border);

            // 顶部标签：循环/受限
            if (node.cycle()) {
                renderNodeBadge(graphics, layout.x(), layout.y(), nw,
                        Component.translatable("screen.jeict.cycle").withStyle(ChatFormatting.YELLOW),
                        0xFFFFAA00, 0xCC553300);
            } else if (node.limited()) {
                renderNodeBadge(graphics, layout.x(), layout.y(), nw,
                        Component.translatable("screen.jeict.limited").withStyle(ChatFormatting.YELLOW),
                        0xFFFFAA00, 0xCC553300);
            }

            // 配方图标（左侧）
            int itemX = layout.itemCenterX() - scale(ITEM_ICON_SIZE) / 2;
            int iconAreaLeft = itemX - scale(RECIPE_ICON_SIZE + NODE_ICON_GAP);
            if (node.recipe() != null) {
                drawRecipeIcon(graphics, node, iconAreaLeft, layout.y() + scale(7));
            }

            // 物品图标
            int itemY = layout.y() + (nh - scale(16)) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(itemX, itemY, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1);
            ItemStack stack = displayStackFor(node);
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(font, stack, 0, 0);
            graphics.pose().popPose();

            if (hasMultipleCandidates(node)) {
                renderDropdownButton(graphics, layout, mouseX, mouseY);
            }
        }
        renderAlternativeDropdown(graphics, mouseX, mouseY);
    }

    private void renderDropdownButton(GuiGraphics graphics, LayoutNode layout, int mouseX, int mouseY) {
        int size = scale(9);
        int x = layout.x() + layout.width() + scale(3);
        int y = layout.y() + (layout.height() - size) / 2;
        int fill = contains(mouseX, mouseY, x, y, size, size) ? 0xFFFFFFFF : 0xE6F4F6F7;
        drawBox(graphics, x, y, size, size, fill, 0xFF6B777C);
        int midX = x + size / 2;
        int topY = y + scale(3);
        graphics.fill(midX - scale(2), topY, midX + scale(3), topY + scale(1), 0xFF3F4C51);
        graphics.fill(midX - scale(1), topY + scale(2), midX + scale(2), topY + scale(3), 0xFF3F4C51);
        graphics.fill(midX, topY + scale(4), midX + scale(1), topY + scale(5), 0xFF3F4C51);
    }

    private void renderAlternativeDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (dropdownNode == null) {
            return;
        }
        LayoutNode layout = layoutFor(dropdownNode);
        if (layout == null) {
            dropdownNode = null;
            return;
        }
        List<ItemStack> alternatives = dropdownNode.alternatives();
        if (alternatives.size() <= 1) {
            dropdownNode = null;
            return;
        }
        int cell = scale(22);
        int columns = Math.min(6, alternatives.size());
        int rows = (alternatives.size() + columns - 1) / columns;
        int width = columns * cell + scale(4);
        int height = rows * cell + scale(4);
        int x = layout.x() + layout.width() + scale(4);
        int y = layout.y();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        drawBox(graphics, x, y, width, height, 0xF4EEF1F2, 0xFF87959B);
        for (int i = 0; i < alternatives.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = x + scale(2) + col * cell;
            int cellY = y + scale(2) + row * cell;
            boolean hovered = contains(mouseX, mouseY, cellX, cellY, cell, cell);
            if (hovered) {
                graphics.fill(cellX, cellY, cellX + cell, cellY + cell, 0xFFFFFFFF);
            }
            ItemStack stack = alternatives.get(i);
            graphics.pose().pushPose();
            graphics.pose().translate(cellX + scale(3), cellY + scale(3), 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    private int nodeFill(TreeNode node) {
        if (node.cycle() || node.limited()) {
            return 0xFFF08A1A;
        }
        // 路径 3：无配方可选的节点用灰色，便于玩家一眼看到"该叶子需要手动指定配方"。
        if (node.noRecipe()) {
            return 0xFF9BA3A6;
        }
        int depth = node.depth() % 4;
        if (depth == 0) {
            return 0xFF1488A6;
        }
        if (depth == 1) {
            return 0xFF11A781;
        }
        if (depth == 2) {
            return 0xFF8EBA4A;
        }
        return node.recipe() == null ? 0xFFF39A17 : 0xFFE88F18;
    }

    private boolean hasMultipleCandidates(TreeNode node) {
        return node.alternatives().size() > 1;
    }

    private void renderNodeBadge(GuiGraphics graphics, int nodeX, int nodeY, int nodeW, Component text, int textColor, int bgColor) {
        int textW = font.width(text);
        int badgeX = nodeX + (nodeW - textW) / 2;
        int badgeY = nodeY - font.lineHeight - 4;
        graphics.fill(badgeX - 3, badgeY - 1, badgeX + textW + 3, badgeY + font.lineHeight + 1, bgColor);
        graphics.drawString(font, text, badgeX, badgeY, textColor, false);
    }

    private void renderTotals(GuiGraphics graphics, int mouseX, int mouseY) {
        LayoutNode root = layoutFor(tree.root());
        if (root == null) {
            return;
        }
        Map<IngredientKey, CostEntry> costs = new HashMap<>();
        Map<IngredientKey, CostEntry> leftovers = new HashMap<>();
        collectCosts(tree.root(), costs, leftovers, 1.0);
        if (costs.isEmpty() && leftovers.isEmpty()) {
            return;
        }

        List<CostDisplayEntry> totalEntries = entriesForCosts(costs);
        for (CostDisplayEntry entry : totalEntries) {
            inventoryCompareEntries.add(new InventoryCompareEntry(entry.key(), entry.stack(), entry.count(), entry.owned(), entry.status()));
        }
        List<CostDisplayEntry> leftoverEntries = entriesForLeftovers(leftovers);
        boolean hasTotals = !totalEntries.isEmpty();
        boolean hasLeftovers = !leftoverEntries.isEmpty();
        int rowCount = (hasTotals ? 1 : 0) + (hasLeftovers ? 1 : 0);
        int rowsHeight = rowCount > 1 ? COST_ROW_HEIGHT * 2 + 8 : COST_ROW_HEIGHT;
        int y = root.y() - scale(rowsHeight) - scale(22);

        if (hasTotals) {
            CostStripLayout totalStrip = renderCostStrip(graphics, Component.translatable("screen.jeict.total_cost"), totalEntries, root.centerX(), y);
            renderInventoryCompareButton(graphics, totalStrip, mouseX, mouseY);
            if (shouldRenderInventoryComparePanel()) {
                ensureInventoryPanelPosition(totalStrip);
            }
        }
        if (hasLeftovers) {
            int leftoverY = hasTotals ? y + scale(COST_ROW_HEIGHT + 8) : y;
            renderCostStrip(graphics, Component.translatable("screen.jeict.leftovers"), leftoverEntries, root.centerX(), leftoverY);
        }
    }

    private List<CostDisplayEntry> entriesForCosts(Map<IngredientKey, CostEntry> entries) {
        List<CostDisplayEntry> displayEntries = new ArrayList<>();
        entries.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().stack().getHoverName().getString()))
                .map(entry -> {
                    IngredientKey key = entry.getKey();
                    CostEntry cost = entry.getValue();
                    int owned = ownedCount(key);
                    InventoryStatus status = inventoryStatus(cost.count(), owned);
                    return new CostDisplayEntry(key, cost.stack(), cost.count(), inventoryBorder(status), owned, status, true);
                })
                .forEach(displayEntries::add);
        return displayEntries;
    }

    private List<CostDisplayEntry> entriesForLeftovers(Map<IngredientKey, CostEntry> entries) {
        List<CostDisplayEntry> displayEntries = new ArrayList<>();
        entries.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().stack().getHoverName().getString()))
                .map(entry -> new CostDisplayEntry(entry.getKey(), entry.getValue().stack(), entry.getValue().count(), COST_LEFTOVER_BORDER, 0, InventoryStatus.MISSING, false))
                .forEach(displayEntries::add);
        return displayEntries;
    }

    private CostStripLayout renderCostStrip(GuiGraphics graphics, Component label, List<CostDisplayEntry> entries, int centerX, int y) {
        int labelWidth = scaledTextWidth(label.getString());
        int totalWidth = 0;
        for (CostDisplayEntry entry : entries) {
            totalWidth += costCellWidth(entry);
        }
        totalWidth += Math.max(0, entries.size() - 1) * scale(COST_CELL_GAP);
        int rowWidth = labelWidth + scale(8) + totalWidth;
        int rowHeight = scale(COST_ROW_HEIGHT);
        int rowX = centerX - rowWidth / 2;
        int x = rowX;
        drawScaledString(graphics, label.getString(), x, y + (rowHeight - scaledFontHeight()) / 2, 0xFF2F3E43, false);
        x += labelWidth + scale(8);
        for (CostDisplayEntry entry : entries) {
            int cellWidth = costCellWidth(entry);
            int cellHeight = rowHeight;
            drawBox(graphics, x, y, cellWidth, cellHeight, 0xF7F9FAFB, entry.border());
            costLayouts.add(new CostLayout(entry.key(), entry.stack(), entry.count(), entry.owned(), entry.status(), entry.inventoryAware(), x, y, cellWidth, cellHeight));

            ItemStack stack = entry.stack().copy();
            stack.setCount(1);
            int iconX = x + scale(4);
            int iconY = y + (cellHeight - scale(16)) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX, iconY, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1);
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(font, stack, 0, 0);
            graphics.pose().popPose();

            String countText = "x" + entry.count();
            drawScaledString(graphics, countText, iconX + scale(20), y + (cellHeight - scaledFontHeight()) / 2, 0xFF2F3E43, false);
            x += cellWidth + scale(COST_CELL_GAP);
        }
        return new CostStripLayout(rowX, y, rowWidth, rowHeight);
    }

    private void renderInventoryCompareButton(GuiGraphics graphics, CostStripLayout totalStrip, int mouseX, int mouseY) {
        int size = scale(14);
        int x = totalStrip.x() + totalStrip.width() + scale(6);
        int y = totalStrip.y() + (totalStrip.height() - size) / 2;
        inventoryCompareButton = new ButtonLayout(x, y, size, size);
        boolean hovered = inventoryCompareButton.contains(mouseX, mouseY);
        int fill = hovered || shouldRenderInventoryComparePanel() ? 0xFFFFFFFF : 0xE6F4F6F7;
        drawBox(graphics, x, y, size, size, fill, INVENTORY_PANEL_BORDER);
        int midX = x + size / 2;
        int midY = y + size / 2;
        int arm = Math.max(1, scale(4));
        int thickness = Math.max(1, scale(1));
        graphics.fill(midX - arm, midY - thickness / 2, midX + arm + thickness, midY - thickness / 2 + thickness, 0xFF2F3E43);
        graphics.fill(midX - thickness / 2, midY - arm, midX - thickness / 2 + thickness, midY + arm + thickness, 0xFF2F3E43);
    }

    private int costCellWidth(CostDisplayEntry entry) {
        return scale(4 + 16 + 5) + scaledTextWidth("x" + entry.count()) + scale(6);
    }

    private int ownedCount(IngredientKey key) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        Inventory inventory = minecraft.player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && key.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private InventoryStatus inventoryStatus(int needed, int owned) {
        if (owned >= needed) {
            return InventoryStatus.ENOUGH;
        }
        if (owned > 0) {
            return InventoryStatus.PARTIAL;
        }
        return InventoryStatus.MISSING;
    }

    private int inventoryBorder(InventoryStatus status) {
        return switch (status) {
            case ENOUGH -> INVENTORY_ENOUGH_BORDER;
            case PARTIAL -> INVENTORY_PARTIAL_BORDER;
            case MISSING -> INVENTORY_MISSING_BORDER;
        };
    }

    private int inventoryStatusColor(InventoryStatus status) {
        return switch (status) {
            case ENOUGH -> INVENTORY_ENOUGH_BORDER;
            case PARTIAL -> INVENTORY_PARTIAL_BORDER;
            case MISSING -> INVENTORY_MISSING_BORDER;
        };
    }

    private ChatFormatting inventoryStatusFormatting(InventoryStatus status) {
        return switch (status) {
            case ENOUGH -> ChatFormatting.GREEN;
            case PARTIAL -> ChatFormatting.GOLD;
            case MISSING -> ChatFormatting.RED;
        };
    }

    private Component inventoryStatusText(InventoryStatus status) {
        return Component.translatable(switch (status) {
            case ENOUGH -> "screen.jeict.inventory_compare.status_enough";
            case PARTIAL -> "screen.jeict.inventory_compare.status_partial";
            case MISSING -> "screen.jeict.inventory_compare.status_missing";
        });
    }

    private Component inventoryCompareStatusText(int needed, int owned, InventoryStatus status) {
        if (status == InventoryStatus.ENOUGH) {
            return inventoryStatusText(status);
        }
        return Component.translatable("screen.jeict.inventory_compare.missing_amount", "x" + Math.max(0, needed - owned));
    }

    private String inventoryFilterText(InventoryFilter filter) {
        return Component.translatable(switch (filter) {
            case ALL -> "screen.jeict.inventory_compare.filter_all";
            case MISSING -> "screen.jeict.inventory_compare.filter_missing";
            case PARTIAL -> "screen.jeict.inventory_compare.filter_partial";
            case ENOUGH -> "screen.jeict.inventory_compare.filter_enough";
        }).getString();
    }

    private boolean shouldRenderInventoryComparePanel() {
        return (inventoryCompareOpen || inventoryComparePinned) && !inventoryCompareEntries.isEmpty();
    }

    private void ensureInventoryPanelPosition(CostStripLayout totalStrip) {
        if (inventoryPanelX >= 0 && inventoryPanelY >= 0) {
            return;
        }
        int panelWidth = inventoryPanelWidth(filteredInventoryCompareEntries());
        inventoryPanelX = totalStrip.x() + totalStrip.width() + scale(24);
        inventoryPanelY = totalStrip.y() + scale(COST_ROW_HEIGHT + 8);
        inventoryPanelX = Math.max(scale(6), Math.min(inventoryPanelX, width - panelWidth - scale(6)));
        inventoryPanelY = Math.max(scale(6), Math.min(inventoryPanelY, height - scale(80)));
    }

    private void renderInventoryComparePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldRenderInventoryComparePanel()) {
            return;
        }
        List<InventoryCompareEntry> filteredEntries = filteredInventoryCompareEntries();
        int panelWidth = inventoryPanelWidth(filteredEntries);
        int panelHeight = inventoryPanelHeight(filteredEntries.size());
        clampInventoryPanelPosition(panelWidth, panelHeight);
        int x = inventoryPanelX;
        int y = inventoryPanelY;
        int padding = scale(INVENTORY_PANEL_PADDING);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 275);
        int shadow = Math.max(1, scale(2));
        graphics.fill(x + shadow, y + shadow, x + panelWidth + shadow, y + panelHeight + shadow, 0x22000000);
        drawBox(graphics, x, y, panelWidth, panelHeight, INVENTORY_PANEL_BG, INVENTORY_PANEL_BORDER);

        int controlSize = scale(INVENTORY_PANEL_CONTROL_SIZE);
        int controlY = y + padding;
        inventoryPinButton = new ButtonLayout(x + padding, controlY, controlSize, controlSize);
        renderInventoryPinButton(graphics, inventoryPinButton, mouseX, mouseY);
        inventoryCloseButton = new ButtonLayout(x + panelWidth - padding - controlSize, controlY, controlSize, controlSize);
        renderInventoryTextButton(graphics, inventoryCloseButton, "x", mouseX, mouseY, false);

        String title = Component.translatable("screen.jeict.inventory_compare.title").getString();
        int titleX = x + (panelWidth - inventoryTextWidth(title)) / 2;
        int titleY = y + padding + (scale(INVENTORY_PANEL_HEADER_HEIGHT) - inventoryFontHeight()) / 2;
        drawInventoryString(graphics, title, titleX, titleY, 0xFF2F3E43, false);

        int filterX = x + padding;
        int filterY = y + padding + scale(INVENTORY_PANEL_HEADER_HEIGHT + 3);
        filterX = renderInventoryFilterButton(graphics, InventoryFilter.ALL, filterX, filterY, mouseX, mouseY);
        filterX = renderInventoryFilterButton(graphics, InventoryFilter.MISSING, filterX, filterY, mouseX, mouseY);
        filterX = renderInventoryFilterButton(graphics, InventoryFilter.PARTIAL, filterX, filterY, mouseX, mouseY);
        renderInventoryFilterButton(graphics, InventoryFilter.ENOUGH, filterX, filterY, mouseX, mouseY);

        int rowY = filterY + scale(INVENTORY_PANEL_FILTER_HEIGHT + 4);
        drawInventoryString(graphics, Component.translatable("screen.jeict.total_cost").getString() + ":", x + padding, rowY, 0xFF2F3E43, false);
        rowY += inventoryFontHeight() + scale(3);
        if (filteredEntries.isEmpty()) {
            String empty = Component.translatable("screen.jeict.inventory_compare.empty").getString();
            drawInventoryString(graphics, empty, x + padding, rowY + scale(4), 0xFF5B666B, false);
        } else {
            int visibleRows = Math.min(INVENTORY_PANEL_MAX_ROWS, filteredEntries.size());
            int maxScroll = Math.max(0, filteredEntries.size() - visibleRows);
            inventoryScrollOffset = Math.max(0, Math.min(inventoryScrollOffset, maxScroll));
            for (int i = 0; i < visibleRows; i++) {
                int idx = i + inventoryScrollOffset;
                if (idx >= filteredEntries.size()) break;
                renderInventoryCompareRow(graphics, filteredEntries.get(idx), x + padding, rowY + i * scale(INVENTORY_PANEL_ROW_HEIGHT), panelWidth - padding * 2);
            }
            if (maxScroll > 0) {
                renderInventoryScrollButtons(graphics, x, rowY, panelWidth, visibleRows, mouseX, mouseY);
            }
        }
        graphics.pose().popPose();
    }

    private int renderInventoryFilterButton(GuiGraphics graphics, InventoryFilter filter, int x, int y, int mouseX, int mouseY) {
        String text = inventoryFilterText(filter);
        int buttonWidth = inventoryFilterButtonWidth(text);
        int height = scale(INVENTORY_PANEL_FILTER_HEIGHT);
        FilterLayout layout = new FilterLayout(filter, x, y, buttonWidth, height);
        inventoryFilterLayouts.add(layout);
        boolean active = inventoryFilter == filter;
        boolean hovered = layout.contains(mouseX, mouseY);
        int fill = active ? 0xFFFFFFFF : hovered ? 0xF2FFFFFF : 0xE6F4F6F7;
        int border = active ? NODE_BORDER_SELECTED : INVENTORY_PANEL_BORDER;
        drawBox(graphics, x, y, buttonWidth, height, fill, border);
        drawInventoryString(graphics, text, x + scale(4), y + (height - inventoryFontHeight()) / 2, 0xFF2F3E43, false);
        return x + buttonWidth + scale(4);
    }

    private void renderInventoryCompareRow(GuiGraphics graphics, InventoryCompareEntry entry, int x, int y, int rowWidth) {
        int height = scale(INVENTORY_PANEL_ROW_HEIGHT);
        drawBox(graphics, x, y, rowWidth, height, 0xE6F4F6F7, inventoryBorder(entry.status()));
        int iconX = x + scale(3);
        int iconY = y + (height - scale(16)) / 2;
        ItemStack stack = entry.stack().copy();
        stack.setCount(1);
        renderPreviewItem(graphics, stack, iconX, iconY);
        inventoryCompareLayouts.add(new InventoryCompareLayout(stack, iconX, iconY, scale(16), scale(16)));

        String needed = Component.translatable("screen.jeict.inventory_compare.needed", "x" + entry.needed()).getString();
        String owned = Component.translatable("screen.jeict.inventory_compare.owned", "x" + entry.owned()).getString();
        String status = inventoryCompareStatusText(entry.needed(), entry.owned(), entry.status()).getString();
        int textY = y + (height - inventoryFontHeight()) / 2;
        int textX = iconX + scale(22);
        drawInventoryString(graphics, needed, textX, textY, 0xFF2F3E43, false);
        textX += inventoryTextWidth(needed) + scale(12);
        drawInventoryString(graphics, owned, textX, textY, 0xFF2F3E43, false);
        textX += inventoryTextWidth(owned) + scale(12);
        drawInventoryString(graphics, status, textX, textY, inventoryStatusColor(entry.status()), false);
    }

    private void renderInventoryPinButton(GuiGraphics graphics, ButtonLayout layout, int mouseX, int mouseY) {
        boolean hovered = layout.contains(mouseX, mouseY);
        int fill = inventoryComparePinned ? 0xFFFFFFFF : hovered ? 0xF2FFFFFF : 0xE6F4F6F7;
        drawBox(graphics, layout.x(), layout.y(), layout.width(), layout.height(), fill, inventoryComparePinned ? NODE_BORDER_SELECTED : INVENTORY_PANEL_BORDER);
        int color = inventoryComparePinned ? NODE_BORDER_SELECTED : 0xFF2F3E43;
        int midX = layout.x() + layout.width() / 2;
        int top = layout.y() + scale(2);
        graphics.fill(midX - scale(3), top, midX + scale(4), top + scale(2), color);
        graphics.fill(midX - scale(1), top + scale(2), midX + scale(2), top + scale(7), color);
        graphics.fill(midX - scale(3), top + scale(6), midX + scale(4), top + scale(7), color);
        graphics.fill(midX, top + scale(7), midX + scale(1), top + scale(10), color);
    }

    private void renderInventoryTextButton(GuiGraphics graphics, ButtonLayout layout, String text, int mouseX, int mouseY, boolean active) {
        boolean hovered = layout.contains(mouseX, mouseY);
        int fill = active || hovered ? 0xFFFFFFFF : 0xE6F4F6F7;
        drawBox(graphics, layout.x(), layout.y(), layout.width(), layout.height(), fill, INVENTORY_PANEL_BORDER);
        drawInventoryString(graphics, text, layout.x() + (layout.width() - inventoryTextWidth(text)) / 2, layout.y() + (layout.height() - inventoryFontHeight()) / 2, 0xFF2F3E43, false);
    }

    private void renderInventoryScrollButtons(GuiGraphics graphics, int panelX, int rowY, int panelWidth, int visibleRows, int mouseX, int mouseY) {
        int btnSize = scale(12);
        int rowsHeight = visibleRows * scale(INVENTORY_PANEL_ROW_HEIGHT);
        int btnX = panelX + panelWidth - scale(INVENTORY_PANEL_PADDING) - btnSize;
        int btnCenterY = rowY + rowsHeight / 2;
        int upY = btnCenterY - btnSize - scale(2);
        int downY = btnCenterY + scale(2);

        boolean upHovered = contains(mouseX, mouseY, btnX, upY, btnSize, btnSize);
        boolean downHovered = contains(mouseX, mouseY, btnX, downY, btnSize, btnSize);
        int upFill = upHovered ? 0xFFFFFFFF : 0xE6F4F6F7;
        int downFill = downHovered ? 0xFFFFFFFF : 0xE6F4F6F7;
        drawBox(graphics, btnX, upY, btnSize, btnSize, upFill, INVENTORY_PANEL_BORDER);
        drawBox(graphics, btnX, downY, btnSize, btnSize, downFill, INVENTORY_PANEL_BORDER);

        int arrowColor = 0xFF2F3E43;
        int midX = btnX + btnSize / 2;
        int arrowW = Math.max(2, scale(5));
        int arrowH = Math.max(2, scale(3));
        for (int i = 0; i < arrowH; i++) {
            graphics.fill(midX - arrowW / 2 + i, upY + btnSize / 2 - arrowH + i, midX + arrowW / 2 - i + 1, upY + btnSize / 2 - arrowH + i + 1, arrowColor);
            graphics.fill(midX - arrowW / 2 + i, downY + btnSize / 2 + arrowH - i - 1, midX + arrowW / 2 - i + 1, downY + btnSize / 2 + arrowH - i, arrowColor);
        }
    }

    private List<InventoryCompareEntry> filteredInventoryCompareEntries() {
        return inventoryCompareEntries.stream()
                .filter(this::passesInventoryFilter)
                .toList();
    }

    private boolean passesInventoryFilter(InventoryCompareEntry entry) {
        return switch (inventoryFilter) {
            case ALL -> true;
            case MISSING -> entry.status() == InventoryStatus.MISSING;
            case PARTIAL -> entry.status() == InventoryStatus.PARTIAL;
            case ENOUGH -> entry.status() == InventoryStatus.ENOUGH;
        };
    }

    private int inventoryPanelWidth(List<InventoryCompareEntry> filteredEntries) {
        int contentWidth = inventoryHeaderWidth();
        contentWidth = Math.max(contentWidth, inventoryFiltersWidth());
        contentWidth = Math.max(contentWidth, inventoryTextWidth(Component.translatable("screen.jeict.total_cost").getString() + ":"));
        if (filteredEntries.isEmpty()) {
            contentWidth = Math.max(contentWidth, inventoryTextWidth(Component.translatable("screen.jeict.inventory_compare.empty").getString()));
        } else {
            for (InventoryCompareEntry entry : filteredEntries) {
                contentWidth = Math.max(contentWidth, inventoryRowContentWidth(entry));
            }
        }
        int panelWidth = contentWidth + scale(INVENTORY_PANEL_PADDING * 2);
        int maxWidth = Math.max(scale(INVENTORY_PANEL_MIN_WIDTH), width - scale(12));
        return Math.min(maxWidth, Math.max(scale(INVENTORY_PANEL_MIN_WIDTH), panelWidth));
    }

    private int inventoryHeaderWidth() {
        String title = Component.translatable("screen.jeict.inventory_compare.title").getString();
        return inventoryTextWidth(title) + scale(INVENTORY_PANEL_CONTROL_SIZE * 2 + 16);
    }

    private int inventoryFiltersWidth() {
        int total = 0;
        for (InventoryFilter filter : InventoryFilter.values()) {
            total += inventoryFilterButtonWidth(inventoryFilterText(filter));
        }
        return total + scale(4) * (InventoryFilter.values().length - 1);
    }

    private int inventoryFilterButtonWidth(String text) {
        return inventoryTextWidth(text) + scale(8);
    }

    private int inventoryRowContentWidth(InventoryCompareEntry entry) {
        String needed = Component.translatable("screen.jeict.inventory_compare.needed", "x" + entry.needed()).getString();
        String owned = Component.translatable("screen.jeict.inventory_compare.owned", "x" + entry.owned()).getString();
        String status = inventoryCompareStatusText(entry.needed(), entry.owned(), entry.status()).getString();
        return scale(3 + 16 + 6) + inventoryTextWidth(needed) + scale(12) + inventoryTextWidth(owned) + scale(12) + inventoryTextWidth(status) + scale(5);
    }

    private int inventoryPanelHeight(int filteredEntryCount) {
        int rows = Math.min(INVENTORY_PANEL_MAX_ROWS, Math.max(1, filteredEntryCount));
        return scale(INVENTORY_PANEL_PADDING * 2 + INVENTORY_PANEL_HEADER_HEIGHT + 3 + INVENTORY_PANEL_FILTER_HEIGHT + 4 + 3)
                + inventoryFontHeight()
                + rows * scale(INVENTORY_PANEL_ROW_HEIGHT);
    }

    private void clampInventoryPanelPosition(int panelWidth, int panelHeight) {
        if (inventoryPanelX < 0) {
            inventoryPanelX = width - panelWidth - scale(8);
        }
        if (inventoryPanelY < 0) {
            inventoryPanelY = scale(40);
        }
        inventoryPanelX = Math.max(scale(6), Math.min(inventoryPanelX, width - panelWidth - scale(6)));
        inventoryPanelY = Math.max(scale(6), Math.min(inventoryPanelY, height - panelHeight - scale(6)));
    }

    private int inventoryTextWidth(String text) {
        return (int) Math.ceil(font.width(text) * zoom * INVENTORY_TEXT_SCALE);
    }

    private int inventoryFontHeight() {
        return Math.max(1, (int) Math.ceil(font.lineHeight * zoom * INVENTORY_TEXT_SCALE));
    }

    private void drawInventoryString(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale((float) (zoom * INVENTORY_TEXT_SCALE), (float) (zoom * INVENTORY_TEXT_SCALE), 1);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private int scaledTextWidth(String text) {
        return (int) Math.ceil(font.width(text) * zoom);
    }

    private int scaledFontHeight() {
        return scale(font.lineHeight);
    }

    private void drawScaledString(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale((float) zoom, (float) zoom, 1);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private void collectCosts(TreeNode node, Map<IngredientKey, CostEntry> costs, Map<IngredientKey, CostEntry> leftovers, double multiplier) {
        if (!node.expanded() || node.cycle() || node.limited() || node.recipe() == null || node.children().isEmpty()) {
            costs.compute(node.key(), (key, entry) -> CostEntry.merge(entry, node.singleStack(), effectiveAmount(node, multiplier)));
            return;
        }

        int actualBatches = effectiveBatches(node, multiplier);
        int leftoverAmount = effectiveLeftoverAmount(node, multiplier, actualBatches);
        if (node != tree.root() && leftoverAmount > 0) {
            leftovers.compute(node.key(), (key, entry) -> CostEntry.merge(entry, node.singleStack(), leftoverAmount));
        }
        double adjustedMultiplier = (double) actualBatches / Math.max(1, node.baseBatches());
        for (TreeNode child : node.children()) {
            collectCosts(child, costs, leftovers, adjustedMultiplier);
        }
    }

    private ItemStack displayStackFor(TreeNode node) {
        ItemStack stack = node.singleStack();
        stack.setCount(effectiveAmount(node));
        return stack;
    }

    private int effectiveAmount(TreeNode node) {
        return effectiveAmount(node, multiplierFor(node));
    }

    private int effectiveAmount(TreeNode node, double multiplier) {
        return Math.max(1, (int) Math.ceil(node.amount() * multiplier));
    }

    private int effectiveBatches(TreeNode node, double multiplier) {
        if (node == tree.root() || node.batches() != node.baseBatches()) {
            return node.batches();
        }
        int required = effectiveAmount(node, multiplier);
        return Math.max(1, (required + node.outputPerBatch() - 1) / node.outputPerBatch());
    }

    private int effectiveLeftoverAmount(TreeNode node, double multiplier, int actualBatches) {
        if (node.recipe() == null) {
            return 0;
        }
        int produced = actualBatches * node.outputPerBatch();
        return Math.max(0, produced - effectiveAmount(node, multiplier));
    }

    private double multiplierFor(TreeNode target) {
        return multiplierFor(tree.root(), target, 1.0).orElse(1.0);
    }

    private Optional<Double> multiplierFor(TreeNode current, TreeNode target, double multiplier) {
        if (current == target) {
            return Optional.of(multiplier);
        }
        double childMultiplier = multiplier;
        if (current.recipe() != null) {
            childMultiplier = (double) effectiveBatches(current, multiplier) / Math.max(1, current.baseBatches());
        }
        for (TreeNode child : current.children()) {
            Optional<Double> found = multiplierFor(child, target, childMultiplier);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private void renderNodeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (InventoryCompareLayout layout : inventoryCompareLayouts) {
            if (layout.contains(mouseX, mouseY)) {
                List<Component> lines = layout.stack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (inventoryCompareButton != null && inventoryCompareButton.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.jeict.inventory_compare.open"), mouseX, mouseY);
            return;
        }
        if (inventoryPinButton != null && inventoryPinButton.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(inventoryComparePinned ? "screen.jeict.inventory_compare.unpin" : "screen.jeict.inventory_compare.pin"), mouseX, mouseY);
            return;
        }
        if (inventoryCloseButton != null && inventoryCloseButton.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.jeict.inventory_compare.close"), mouseX, mouseY);
            return;
        }
        Optional<ItemStack> hoveredAlternative = alternativeAt(mouseX, mouseY);
        if (hoveredAlternative.isPresent()) {
            List<Component> lines = hoveredAlternative.get().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        for (CostLayout layout : costLayouts) {
            if (layout.contains(mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>(layout.stack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL));
                if (layout.inventoryAware()) {
                    lines.add(Component.translatable("screen.jeict.inventory_compare.needed", "x" + layout.needed()).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("screen.jeict.inventory_compare.owned", "x" + layout.owned()).withStyle(ChatFormatting.GRAY));
                    lines.add(inventoryCompareStatusText(layout.needed(), layout.owned(), layout.status()).copy().withStyle(inventoryStatusFormatting(layout.status())));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        for (LayoutNode layout : layoutNodes) {
            if (!layout.contains(mouseX, mouseY)) {
                continue;
            }
            TreeNode node = layout.node();
            if (node.recipe() != null && !node.children().isEmpty() && layout.itemContains(mouseX, mouseY)) {
                List<Component> lines = node.displayStack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
                renderCombinedRecipeTooltip(graphics, node, lines, mouseX, mouseY);
                return;
            }
            List<Component> lines = node.displayStack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    private void renderCombinedRecipeTooltip(GuiGraphics graphics, TreeNode node, List<Component> lines, int mouseX, int mouseY) {
        Optional<IRecipeLayoutDrawable<?>> drawableOptional = recipePreviewCache.computeIfAbsent(node.recipe().id(), id -> createRecipePreview(node));
        if (drawableOptional.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            int previewAnchorY = mouseY + tooltipHeight(lines) + scale(12);
            renderRecipePreview(graphics, node, mouseX, previewAnchorY);
            return;
        }

        IRecipeLayoutDrawable<?> drawable = drawableOptional.get();
        Rect2i rect = drawable.getRectWithBorder();
        int textWidth = tooltipTextWidth(lines);
        int textHeight = tooltipHeight(lines);
        int padding = scale(5);
        int gap = scale(5);
        int contentWidth = Math.max(textWidth, rect.getWidth());
        int contentHeight = textHeight + gap + rect.getHeight();
        int panelWidth = contentWidth + padding * 2;
        int panelHeight = contentHeight + padding * 2;
        int x = Math.min(mouseX + scale(12), width - panelWidth - scale(4));
        int y = Math.min(mouseY - scale(12), height - panelHeight - scale(4));
        x = Math.max(scale(4), x);
        y = Math.max(scale(4), y);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        drawTooltipBox(graphics, x, y, panelWidth, panelHeight);
        int textX = x + padding;
        int textY = y + padding;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), textX, textY + i * scale(10), 0xFFFFFFFF, false);
        }
        int recipeX = x + padding;
        int recipeY = textY + textHeight + gap;
        drawable.setPosition(recipeX, recipeY);
        drawable.drawRecipe(graphics, mouseX, mouseY);
        drawable.drawOverlays(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private int tooltipTextWidth(List<Component> lines) {
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, scale(font.width(line)));
        }
        return width;
    }

    private int tooltipHeight(List<Component> lines) {
        if (lines.isEmpty()) {
            return 0;
        }
        return scale(8 + (lines.size() - 1) * 10);
    }

    private void drawTooltipBox(GuiGraphics graphics, int x, int y, int width, int height) {
        int background = 0xF0100010;
        int borderTop = 0xFF500080;
        int borderBottom = 0xFF280040;
        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x + 1, y, x + width - 1, y + 1, borderTop);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, borderBottom);
        graphics.fill(x, y + 1, x + 1, y + height - 1, borderTop);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, borderBottom);
    }

    private void renderRecipePreview(GuiGraphics graphics, TreeNode node, int mouseX, int anchorY) {
        int cell = scale(24);
        int gap = scale(6);
        int padding = scale(8);
        int inputCount = Math.max(1, node.children().size());
        int columns = Math.min(4, inputCount);
        int rows = (inputCount + columns - 1) / columns;
        int gridWidth = columns * cell;
        int gridHeight = rows * cell;
        int arrowWidth = scale(18);
        int headerHeight = scale(18);
        int panelWidth = padding * 2 + gridWidth + gap + arrowWidth + gap + cell;
        int panelHeight = padding * 2 + headerHeight + Math.max(gridHeight, cell);
        int x = Math.min(mouseX + scale(12), width - panelWidth - scale(4));
        int y = anchorY;
        if (y + panelHeight > height - scale(4)) {
            y = anchorY - panelHeight - tooltipGap();
        }
        x = Math.max(scale(4), x);
        y = Math.max(scale(4), y);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawBox(graphics, x, y, panelWidth, panelHeight, 0xF7EEF1F2, 0xFF87959B);
        String title = node.recipe().id().getPath();
        int maxTitleWidth = unscale(panelWidth - padding * 2);
        if (font.width(title) > maxTitleWidth) {
            title = font.plainSubstrByWidth(title, maxTitleWidth - font.width("...")) + "...";
        }
        drawScaledString(graphics, title, x + padding, y + scale(5), 0xFF2F3E43, false);

        int gridX = x + padding;
        int gridY = y + padding + headerHeight;
        for (int i = 0; i < node.children().size(); i++) {
            TreeNode child = node.children().get(i);
            int col = i % columns;
            int row = i / columns;
            int itemX = gridX + col * cell + scale(4);
            int itemY = gridY + row * cell + scale(4);
            renderPreviewItem(graphics, displayStackFor(child), itemX, itemY);
        }

        int arrowX = gridX + gridWidth + gap;
        int arrowY = gridY + Math.max(0, (Math.max(gridHeight, cell) - scale(8)) / 2);
        graphics.fill(arrowX, arrowY + scale(3), arrowX + arrowWidth - scale(4), arrowY + scale(5), 0xFF3F4C51);
        graphics.fill(arrowX + arrowWidth - scale(6), arrowY, arrowX + arrowWidth - scale(4), arrowY + scale(8), 0xFF3F4C51);
        graphics.fill(arrowX + arrowWidth - scale(4), arrowY + scale(1), arrowX + arrowWidth - scale(2), arrowY + scale(7), 0xFF3F4C51);

        int outputX = arrowX + arrowWidth + gap + scale(4);
        int outputY = gridY + Math.max(0, (Math.max(gridHeight, cell) - scale(16)) / 2);
        renderPreviewItem(graphics, displayStackFor(node), outputX, outputY);
        graphics.pose().popPose();
    }

    private void renderPreviewItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale((float) zoom, (float) zoom, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(font, stack, 0, 0);
        graphics.pose().popPose();
    }

    private Optional<ItemStack> alternativeAt(int mouseX, int mouseY) {
        if (dropdownNode == null) {
            return Optional.empty();
        }
        LayoutNode layout = layoutFor(dropdownNode);
        if (layout == null) {
            return Optional.empty();
        }
        List<ItemStack> alternatives = dropdownNode.alternatives();
        if (alternatives.size() <= 1) {
            return Optional.empty();
        }
        int cell = scale(22);
        int columns = Math.min(6, alternatives.size());
        int x = layout.x() + layout.width() + scale(4);
        int y = layout.y();
        for (int i = 0; i < alternatives.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = x + scale(2) + col * cell;
            int cellY = y + scale(2) + row * cell;
            if (contains(mouseX, mouseY, cellX, cellY, cell, cell)) {
                return Optional.of(alternatives.get(i));
            }
        }
        return Optional.empty();
    }

    private boolean renderJeiRecipePreview(GuiGraphics graphics, TreeNode node, int mouseX, int anchorY) {
        Optional<IRecipeLayoutDrawable<?>> drawableOptional = recipePreviewCache.computeIfAbsent(node.recipe().id(), id -> createRecipePreview(node));
        if (drawableOptional.isEmpty()) {
            return false;
        }
        IRecipeLayoutDrawable<?> drawable = drawableOptional.get();
        Rect2i rect = drawable.getRectWithBorder();
        int previewWidth = rect.getWidth();
        int previewHeight = rect.getHeight();
        int x = Math.min(mouseX + scale(12), width - previewWidth - scale(4));
        int y = anchorY;
        if (y + previewHeight > height - scale(4)) {
            y = anchorY - previewHeight - tooltipGap();
        }
        x = Math.max(scale(4), x);
        y = Math.max(scale(4), y);
        drawable.setPosition(x, y);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawable.drawRecipe(graphics, mouseX, anchorY);
        drawable.drawOverlays(graphics, mouseX, anchorY);
        graphics.pose().popPose();
        return true;
    }

    private int tooltipGap() {
        return scale(8);
    }

    private Optional<IRecipeLayoutDrawable<?>> createRecipePreview(TreeNode node) {
        return JeiRuntimeAccess.get().flatMap(runtime -> {
            List<IFocus<?>> focuses = List.of(createOutputFocus(runtime, node));
            return findRecipeMatch(runtime, node.recipe(), focuses)
                    .flatMap(match -> createRecipeLayoutDrawable(runtime, match, focuses));
        });
    }

    private <T> Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable(IJeiRuntime runtime, JeiRecipeMatch<T> match, List<IFocus<?>> focuses) {
        return runtime.getRecipeManager()
                .createRecipeLayoutDrawable(match.category(), match.recipe(), runtime.getJeiHelpers().getFocusFactory().createFocusGroup(focuses))
                .map(drawable -> drawable);
    }

    private void drawRecipeIcon(GuiGraphics graphics, TreeNode node, int x, int y) {
        drawBox(graphics, x - scale(1), y - scale(1), scale(RECIPE_ICON_SIZE), scale(RECIPE_ICON_SIZE), 0x33FFFFFF, 0xFFFFFFFF);
        Optional<IDrawable> icon = recipeIconCache.computeIfAbsent(node.recipe().id(), id -> findRecipeIcon(node));
        if (icon.isPresent()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1);
            icon.get().draw(graphics, 0, 0);
            graphics.pose().popPose();
            return;
        }
        drawFallbackRecipeGlyph(graphics, x, y);
    }

    private Optional<IDrawable> findRecipeIcon(TreeNode node) {
        return JeiRuntimeAccess.get().flatMap(runtime -> {
            List<IFocus<?>> focuses = List.of(createOutputFocus(runtime, node));
            return findRecipeMatch(runtime, node.recipe(), focuses)
                    .flatMap(match -> {
                        IDrawable icon = match.category().getIcon();
                        if (icon != null) {
                            return Optional.of(icon);
                        }
                        return runtime.getRecipeManager()
                                .createRecipeCatalystLookup(match.category().getRecipeType())
                                .getItemStack()
                                .findFirst()
                                .map(stack -> runtime.getJeiHelpers().getGuiHelper().createDrawableItemStack(stack));
                    });
        });
    }

    private void drawFallbackRecipeGlyph(GuiGraphics graphics, int x, int y) {
        int cell = Math.max(1, scale(4));
        graphics.fill(x, y, x + scale(18), y + scale(18), 0x66000000);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = x + scale(2) + col * scale(5);
                int cy = y + scale(2) + row * scale(5);
                graphics.fill(cx, cy, cx + cell, cy + cell, 0xFFFFFFFF);
            }
        }
    }

    private void drawBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private void drawHLine(GuiGraphics graphics, int x1, int x2, int y) {
        int thickness = lineThickness();
        graphics.fill(Math.min(x1, x2), y - thickness / 2, Math.max(x1, x2) + thickness, y - thickness / 2 + thickness, LINE_COLOR);
    }

    private void drawVLine(GuiGraphics graphics, int x, int y1, int y2) {
        int thickness = lineThickness();
        graphics.fill(x - thickness / 2, Math.min(y1, y2), x - thickness / 2 + thickness, Math.max(y1, y2) + thickness, LINE_COLOR);
    }

    private int lineThickness() {
        return Math.max(1, scale(1));
    }

    private boolean clickInventoryComparePanel(int mouseX, int mouseY) {
        if (!shouldRenderInventoryComparePanel()) {
            return false;
        }
        if (inventoryCloseButton != null && inventoryCloseButton.contains(mouseX, mouseY)) {
            inventoryCompareOpen = false;
            inventoryComparePinned = false;
            inventoryPanelDragging = false;
            return true;
        }
        if (inventoryPinButton != null && inventoryPinButton.contains(mouseX, mouseY)) {
            inventoryComparePinned = !inventoryComparePinned;
            inventoryCompareOpen = true;
            return true;
        }
        for (FilterLayout layout : inventoryFilterLayouts) {
            if (layout.contains(mouseX, mouseY)) {
                inventoryFilter = layout.filter();
                inventoryScrollOffset = 0;
                return true;
            }
        }
        List<InventoryCompareEntry> filteredEntries = filteredInventoryCompareEntries();
        int panelWidth = inventoryPanelWidth(filteredEntries);
        int panelHeight = inventoryPanelHeight(filteredEntries.size());
        if (inventoryPanelX >= 0 && inventoryPanelY >= 0 && contains(mouseX, mouseY, inventoryPanelX, inventoryPanelY, panelWidth, panelHeight)) {
            inventoryPanelDragging = true;
            inventoryPanelDragOffsetX = mouseX - inventoryPanelX;
            inventoryPanelDragOffsetY = mouseY - inventoryPanelY;
            return true;
        }
        if (!inventoryComparePinned) {
            inventoryCompareOpen = false;
            inventoryPanelDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (button == 0 && clickInventoryComparePanel(mx, my)) {
            return true;
        }
        if (button == 0 && inventoryCompareButton != null && inventoryCompareButton.contains(mx, my)) {
            dropdownNode = null;
            if (inventoryComparePinned) {
                inventoryCompareOpen = true;
            } else {
                inventoryCompareOpen = !inventoryCompareOpen;
            }
            return true;
        }
        if (button == 0 && dropdownNode != null && clickAlternativeDropdown(mx, my)) {
            return true;
        }
        if (button == 0) {
            for (CostLayout layout : costLayouts) {
                if (layout.contains(mx, my)) {
                    dropdownNode = null;
                    selected = findOrCreateSelection(layout.stack());
                    batchInput = "";
                    openRecipeInJei(selected);
                    return true;
                }
            }
        }
        if (button == 0) {
            for (LayoutNode layout : layoutNodes) {
                if (clickDropdownButton(layout, (int) mouseX, (int) mouseY)) {
                    selected = layout.node();
                    batchInput = "";
                    return true;
                }
            }
        }
        for (LayoutNode layout : layoutNodes) {
            if (layout.contains((int) mouseX, (int) mouseY)) {
                selected = layout.node();
                batchInput = "";
                if (button == 0) {
                    dropdownNode = null;
                    openRecipeInJei(selected);
                } else if (button == 1 && !selected.children().isEmpty()) {
                    dropdownNode = null;
                    setExpandedForMatchingNodes(selected, !selected.expanded());
                }
                return true;
            }
        }
        dropdownNode = null;
        if (button == 1) {
            dragging = true;
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickDropdownButton(LayoutNode layout, int mouseX, int mouseY) {
        if (!hasMultipleCandidates(layout.node())) {
            return false;
        }
        int size = scale(9);
        int x = layout.x() + layout.width() + scale(3);
        int y = layout.y() + (layout.height() - size) / 2;
        if (!contains(mouseX, mouseY, x, y, size, size)) {
            return false;
        }
        dropdownNode = dropdownNode == layout.node() ? null : layout.node();
        return true;
    }

    private boolean clickAlternativeDropdown(int mouseX, int mouseY) {
        LayoutNode layout = layoutFor(dropdownNode);
        if (layout == null) {
            dropdownNode = null;
            return false;
        }
        List<ItemStack> alternatives = dropdownNode.alternatives();
        int cell = scale(22);
        int columns = Math.min(6, alternatives.size());
        int x = layout.x() + layout.width() + scale(4);
        int y = layout.y();
        for (int i = 0; i < alternatives.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = x + scale(2) + col * cell;
            int cellY = y + scale(2) + row * cell;
            if (contains(mouseX, mouseY, cellX, cellY, cell, cell)) {
                selectAlternative(dropdownNode, alternatives.get(i));
                dropdownNode = null;
                return true;
            }
        }
        dropdownNode = null;
        return false;
    }

    private void setExpandedForMatchingNodes(TreeNode source, boolean expanded) {
        for (TreeNode node : matchingNodes(source.key())) {
            if (!node.children().isEmpty()) {
                node.expanded(expanded);
            }
        }
        // 路径 6：折叠/展开会改变子树可见性，需要重建 layout。
        markLayoutDirty();
    }

    /** 路径 6：把布局判脏集中到一个方法，避免散落各处的赋值被漏掉。 */
    private void markLayoutDirty() {
        layoutDirty = true;
    }

    private boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private TreeNode findOrCreateSelection(ItemStack stack) {
        IngredientKey key = IngredientKey.of(stack);
        return findNodeByKey(tree.root(), key)
                .orElseGet(() -> new TreeNode(key, stack, Math.max(1, stack.getCount()), 0));
    }

    private Optional<TreeNode> findNodeByKey(TreeNode node, IngredientKey key) {
        if (node.key().equals(key)) {
            return Optional.of(node);
        }
        for (TreeNode child : node.children()) {
            Optional<TreeNode> found = findNodeByKey(child, key);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && inventoryPanelDragging) {
            List<InventoryCompareEntry> filteredEntries = filteredInventoryCompareEntries();
            int panelWidth = inventoryPanelWidth(filteredEntries);
            int panelHeight = inventoryPanelHeight(filteredEntries.size());
            inventoryPanelX = (int) mouseX - inventoryPanelDragOffsetX;
            inventoryPanelY = (int) mouseY - inventoryPanelDragOffsetY;
            clampInventoryPanelPosition(panelWidth, panelHeight);
            return true;
        }
        if (button == 1 && dragging) {
            panX += (int) mouseX - lastMouseX;
            panY += (int) mouseY - lastMouseY;
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && inventoryPanelDragging) {
            inventoryPanelDragging = false;
            return true;
        }
        if (button == 1) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasControlDown()) {
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.5, zoom + delta * 0.1));
            panX = (int) (mouseX - (mouseX - panX) * (zoom / oldZoom));
            panY = (int) (mouseY - (mouseY - panY) * (zoom / oldZoom));
            return true;
        }
        if (shouldRenderInventoryComparePanel()) {
            List<InventoryCompareEntry> filteredEntries = filteredInventoryCompareEntries();
            int visibleRows = Math.min(INVENTORY_PANEL_MAX_ROWS, filteredEntries.size());
            int maxScroll = Math.max(0, filteredEntries.size() - visibleRows);
            int mx = (int) mouseX;
            int my = (int) mouseY;
            int panelWidth = inventoryPanelWidth(filteredEntries);
            int panelHeight = inventoryPanelHeight(filteredEntries.size());
            if (mx >= inventoryPanelX && mx < inventoryPanelX + panelWidth
                    && my >= inventoryPanelY && my < inventoryPanelY + panelHeight) {
                inventoryScrollOffset = Math.max(0, Math.min(maxScroll, inventoryScrollOffset + (delta > 0 ? -1 : 1)));
                return true;
            }
        }
        for (LayoutNode layout : layoutNodes) {
            if (layout.contains((int) mouseX, (int) mouseY) && canEditBatches(layout.node())) {
                int step = hasShiftDown() ? 10 : 1;
                layout.node().batches(layout.node().batches() + (delta > 0 ? step : -step));
                batchInput = "";
                selected = layout.node();
                markLayoutDirty();
                return true;
            }
        }
        if (hasShiftDown()) {
            panX += (int) (delta * 28);
        } else {
            panY += (int) (delta * 28);
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (selected != null && canEditBatches(selected) && Character.isDigit(codePoint)) {
            if (batchInput.length() < 5) {
                batchInput += codePoint;
                selected.batches(Integer.parseInt(batchInput));
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected != null && canEditBatches(selected)) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !batchInput.isEmpty()) {
                batchInput = batchInput.substring(0, batchInput.length() - 1);
                selected.batches(batchInput.isEmpty() ? selected.baseBatches() : Integer.parseInt(batchInput));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                batchInput = "";
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int scale(int value) {
        return Math.max(1, (int) Math.round(value * zoom));
    }

    private int unscale(int value) {
        return Math.max(1, (int) Math.floor(value / zoom));
    }

    private boolean canEditBatches(TreeNode node) {
        return node.recipe() != null;
    }

    private void openRecipeInJei(TreeNode node) {
        JeiRuntimeAccess.get().ifPresent(runtime -> {
            List<IFocus<?>> focuses = List.of(createOutputFocus(runtime, node));
            runtime.getRecipesGui().show(focuses);
        });
    }

    public void transferRecipeFromJei(Object recipe, IRecipeSlotsView recipeSlots) {
        if (selected == null) {
            return;
        }
        InputData slotInputData = collectInputs(recipeSlots);
        InputData inputData = slotInputData;
        if (recipe instanceof Recipe<?> minecraftRecipe) {
            List<Ingredient> ingredients = minecraftRecipe.getIngredients();
            InputData recipeInputData = collectInputs(ingredients);
            if (!recipeInputData.counts().isEmpty() && recipeInputData.counts().size() == slotInputData.counts().size()) {
                inputData = recipeInputData;
            } else if (!slotInputData.counts().isEmpty()) {
                inputData = slotInputData.withAlternativesFrom(ingredients);
            } else {
                inputData = recipeInputData;
            }
        }
        if (inputData.counts().isEmpty()) {
            return;
        }

        ItemStack output = extractOutputStack(recipe, recipeSlots);
        if (!selected.key().matches(output)) {
            return;
        }
        if (recipe instanceof Recipe<?> minecraftRecipe) {
            RecipeSelectionMemory.remember(output, minecraftRecipe.getId());
        }
        List<TreeNode> matchingNodes = matchingNodes(selected.key());
        for (TreeNode node : matchingNodes) {
            applyRecipeSelection(node, recipe, output, inputData);
        }
        recipeIconCache.clear();
        recipePreviewCache.clear();
        batchInput = "";
    }

    private InputData collectInputs(IRecipeSlotsView recipeSlots) {
        Map<IngredientKey, ItemStack> displayStacks = new LinkedHashMap<>();
        Map<IngredientKey, Integer> counts = new LinkedHashMap<>();
        Map<IngredientKey, List<ItemStack>> alternativesByKey = new LinkedHashMap<>();
        for (IRecipeSlotView slot : recipeSlots.getSlotViews()) {
            if (slot.getRole() != RecipeIngredientRole.INPUT) {
                continue;
            }
            Optional<ItemStack> stackOptional = slot.getDisplayedItemStack()
                    .or(() -> slot.getItemStacks().findFirst());
            if (stackOptional.isEmpty() || stackOptional.get().isEmpty()) {
                continue;
            }
            ItemStack stack = stackOptional.get().copy();
            int count = Math.max(1, stack.getCount());
            stack.setCount(1);
            IngredientKey key = IngredientKey.of(stack);
            displayStacks.putIfAbsent(key, stack);
            counts.merge(key, count, Integer::sum);
            List<ItemStack> alternatives = slot.getItemStacks()
                    .filter(candidate -> !candidate.isEmpty())
                    .map(candidate -> {
                        ItemStack alternative = candidate.copy();
                        alternative.setCount(1);
                        return alternative;
                    })
                    .toList();
            if (!alternatives.isEmpty()) {
                alternativesByKey.putIfAbsent(key, alternatives);
            }
        }
        return new InputData(displayStacks, counts, alternativesByKey);
    }

    private InputData collectInputs(List<Ingredient> ingredients) {
        Map<IngredientKey, ItemStack> displayStacks = new LinkedHashMap<>();
        Map<IngredientKey, Integer> counts = new LinkedHashMap<>();
        Map<IngredientKey, List<ItemStack>> alternativesByKey = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack[] alternatives = ingredient.getItems();
            if (alternatives.length == 0) {
                continue;
            }
            ItemStack childStack = RecipeSelectionMemory.selectedAlternative(alternatives[0])
                    .filter(ingredient::test)
                    .orElse(alternatives[0])
                    .copy();
            int count = Math.max(1, childStack.getCount());
            childStack.setCount(1);
            IngredientKey key = IngredientKey.of(childStack);
            displayStacks.putIfAbsent(key, childStack);
            counts.merge(key, count, Integer::sum);
            alternativesByKey.putIfAbsent(key, normalizeAlternatives(alternatives));
        }
        return new InputData(displayStacks, counts, alternativesByKey);
    }

    private List<ItemStack> normalizeAlternatives(ItemStack[] alternatives) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack alternative : alternatives) {
            if (alternative.isEmpty()) {
                continue;
            }
            ItemStack stack = alternative.copy();
            stack.setCount(1);
            stacks.add(stack);
        }
        return stacks;
    }

    private void selectAlternative(TreeNode node, ItemStack alternative) {
        RecipeSelectionMemory.rememberAlternative(node.alternatives().get(0), alternative);
        List<TreeNode> targets = matchingAlternativeNodes(node);
        for (TreeNode target : targets) {
            target.replaceStack(alternative);
            rebuildSelectedNode(target);
        }
        selected = node;
        batchInput = "";
        recipeIconCache.clear();
        recipePreviewCache.clear();
        // 路径 6：替代品切换会改变 children 与 displayStack，需要重建 layout。
        markLayoutDirty();
    }

    private void rebuildSelectedNode(TreeNode node) {
        List<ItemStack> alternatives = node.alternatives();
        node.children().clear();
        node.recipe(null);
        node.limited(false);
        node.cycle(false);
        Minecraft minecraft = Minecraft.getInstance();
        RecipeGraphCache.get(minecraft).ifPresent(graph -> {
            TreeNode rebuilt = graph.createNode(node.singleStack(), node.amount(), node.depth());
            node.recipe(rebuilt.recipe());
            node.children().addAll(rebuilt.children());
            node.baseBatches(rebuilt.baseBatches());
            node.outputPerBatch(rebuilt.outputPerBatch());
            node.batches(rebuilt.batches());
            node.limited(rebuilt.limited());
            node.cycle(rebuilt.cycle());
        });
        node.alternatives(alternatives);
        // 路径 6：children 重新挂载，必须重建 layout。
        markLayoutDirty();
    }

    private void applyRecipeSelection(TreeNode node, Object recipe, ItemStack output, InputData inputData) {
        int outputCount = Math.max(1, output.getCount());
        int batches = Math.max(1, (node.amount() + outputCount - 1) / outputCount);

        if (recipe instanceof Recipe<?> minecraftRecipe) {
            node.recipe(new RecipeNode(minecraftRecipe.getId(), minecraftRecipe, output, minecraftRecipe.getIngredients()));
        }
        node.children().clear();
        node.baseBatches(batches);
        node.outputPerBatch(outputCount);
        node.batches(batches);
        node.limited(false);
        node.cycle(false);
        node.expanded(true);

        Minecraft minecraft = Minecraft.getInstance();
        Optional<RecipeGraphCache> cache = RecipeGraphCache.get(minecraft);
        for (Map.Entry<IngredientKey, Integer> entry : inputData.counts().entrySet()) {
            ItemStack childStack = inputData.displayStacks().get(entry.getKey()).copy();
            int amount = entry.getValue() * batches;
            TreeNode child = cache
                    .map(graph -> graph.createNode(childStack, amount, node.depth() + 1))
                    .orElseGet(() -> new TreeNode(entry.getKey(), childStack, amount, node.depth() + 1));
            child.alternatives(inputData.alternativesByKey().getOrDefault(entry.getKey(), List.of()));
            node.children().add(child);
        }
        // 路径 6：从 JEI 切了配方，子节点全替换，需要重建 layout。
        markLayoutDirty();
    }

    private List<TreeNode> matchingNodes(IngredientKey key) {
        List<TreeNode> matches = new ArrayList<>();
        collectMatchingNodes(tree.root(), key, matches);
        return matches;
    }

    private List<TreeNode> matchingAlternativeNodes(TreeNode source) {
        List<TreeNode> matches = new ArrayList<>();
        collectMatchingAlternativeNodes(tree.root(), source, matches);
        return matches;
    }

    private void collectMatchingAlternativeNodes(TreeNode node, TreeNode source, List<TreeNode> matches) {
        if (sameAlternatives(node, source)) {
            matches.add(node);
        }
        for (TreeNode child : node.children()) {
            collectMatchingAlternativeNodes(child, source, matches);
        }
    }

    private boolean sameAlternatives(TreeNode a, TreeNode b) {
        List<ItemStack> left = a.alternatives();
        List<ItemStack> right = b.alternatives();
        if (left.size() <= 1 || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!IngredientKey.of(left.get(i)).equals(IngredientKey.of(right.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private void collectMatchingNodes(TreeNode node, IngredientKey key, List<TreeNode> matches) {
        if (node.key().equals(key)) {
            matches.add(node);
        }
        for (TreeNode child : node.children()) {
            collectMatchingNodes(child, key, matches);
        }
    }

    private ItemStack extractOutputStack(Object recipe, IRecipeSlotsView recipeSlots) {
        Optional<ItemStack> outputSlot = recipeSlots.getSlotViews().stream()
                .filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
                .map(slot -> slot.getDisplayedItemStack().or(() -> slot.getItemStacks().findFirst()))
                .flatMap(Optional::stream)
                .filter(stack -> !stack.isEmpty())
                .findFirst();
        if (outputSlot.isPresent()) {
            return outputSlot.get().copy();
        }
        if (recipe instanceof Recipe<?> minecraftRecipe && minecraft != null && minecraft.level != null) {
            RegistryAccess registries = minecraft.level.registryAccess();
            ItemStack output = minecraftRecipe.getResultItem(registries);
            if (!output.isEmpty()) {
                return output.copy();
            }
        }
        return selected.singleStack();
    }

    private IFocus<ItemStack> createOutputFocus(IJeiRuntime runtime, TreeNode node) {
        return runtime.getJeiHelpers()
                .getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, node.singleStack());
    }

    private Optional<JeiRecipeMatch<?>> findRecipeMatch(IJeiRuntime runtime, RecipeNode recipeNode, List<IFocus<?>> focuses) {
        IRecipeManager recipeManager = runtime.getRecipeManager();
        Recipe<?> recipe = recipeNode.recipe();
        return recipeManager.createRecipeCategoryLookup()
                .limitFocus(focuses)
                .get()
                .map(category -> createRecipeMatch(category, recipe))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<JeiRecipeMatch<?>> createRecipeMatch(IRecipeCategory category, Recipe<?> recipe) {
        Class recipeClass = category.getRecipeType().getRecipeClass();
        if (!recipeClass.isInstance(recipe)) {
            return Optional.empty();
        }
        Object castRecipe = recipeClass.cast(recipe);
        if (!category.isHandled(castRecipe)) {
            return Optional.empty();
        }
        return Optional.of(new JeiRecipeMatch(category, castRecipe));
    }

    /**
     * 路径 8：构造一个带 size 上限的 LRU 缓存，避免 IRecipeLayoutDrawable / IDrawable 等
     * 持有 GL 资源的对象在大树上无界累积导致显存占用。
     * <p>访问顺序为 true 的 LinkedHashMap 在 put / get 时都会触发 reorder，
     * {@code removeEldestEntry} 返回 true 即淘汰最旧条目。
     */
    private static <K, V> Map<K, V> newLruCache(int maxSize) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }


    public static class ContainerTransfer extends AbstractContainerMenu {
        private RecipeTreeScreen screen;

        public ContainerTransfer() {
            super(null, 0);
        }

        public RecipeTreeScreen getScreen() {
            return screen;
        }

        private void setScreen(RecipeTreeScreen screen) {
            this.screen = screen;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private record CostEntry(ItemStack stack, int count) {
        static CostEntry merge(CostEntry entry, ItemStack stack, int count) {
            if (entry == null) {
                return new CostEntry(stack.copy(), count);
            }
            return new CostEntry(entry.stack(), entry.count() + count);
        }
    }

    private enum InventoryStatus {
        ENOUGH,
        PARTIAL,
        MISSING
    }

    private enum InventoryFilter {
        ALL,
        MISSING,
        PARTIAL,
        ENOUGH
    }

    private record CostDisplayEntry(IngredientKey key, ItemStack stack, int count, int border, int owned, InventoryStatus status, boolean inventoryAware) {
    }

    private record CostStripLayout(int x, int y, int width, int height) {
    }

    private record CostLayout(IngredientKey key, ItemStack stack, int needed, int owned, InventoryStatus status, boolean inventoryAware, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record InventoryCompareEntry(IngredientKey key, ItemStack stack, int needed, int owned, InventoryStatus status) {
    }

    private record InventoryCompareLayout(ItemStack stack, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record ButtonLayout(int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record FilterLayout(InventoryFilter filter, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record InputData(Map<IngredientKey, ItemStack> displayStacks,
                             Map<IngredientKey, Integer> counts,
                             Map<IngredientKey, List<ItemStack>> alternativesByKey) {
        private InputData withAlternativesFrom(List<Ingredient> ingredients) {
            Map<IngredientKey, List<ItemStack>> mergedAlternatives = new LinkedHashMap<>(alternativesByKey);
            for (Ingredient ingredient : ingredients) {
                ItemStack[] alternatives = ingredient.getItems();
                if (alternatives.length <= 1) {
                    continue;
                }
                List<ItemStack> normalized = new ArrayList<>();
                for (ItemStack alternative : alternatives) {
                    if (alternative.isEmpty()) {
                        continue;
                    }
                    ItemStack stack = alternative.copy();
                    stack.setCount(1);
                    normalized.add(stack);
                }
                if (normalized.isEmpty()) {
                    continue;
                }
                for (Map.Entry<IngredientKey, ItemStack> entry : displayStacks.entrySet()) {
                    if (ingredient.test(entry.getValue())) {
                        mergedAlternatives.putIfAbsent(entry.getKey(), normalized);
                    }
                }
            }
            return new InputData(displayStacks, counts, mergedAlternatives);
        }
    }

    private record JeiRecipeMatch<T>(IRecipeCategory<T> category, T recipe) {
        private void show(IRecipesGui recipesGui, List<IFocus<?>> focuses) {
            recipesGui.showRecipes(category, List.of(recipe), focuses);
        }
    }
}
