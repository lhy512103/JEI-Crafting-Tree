package com.lhy.jeict.client.screen;

import com.lhy.jeict.integration.JeiRuntimeAccess;
import com.lhy.jeict.tree.IngredientKey;
import com.lhy.jeict.tree.RecipeGraphCache;
import com.lhy.jeict.tree.RecipeNode;
import com.lhy.jeict.tree.RecipeSelectionMemory;
import com.lhy.jeict.tree.RecipeTree;
import com.lhy.jeict.tree.TreeNode;
import mezz.jei.api.constants.VanillaTypes;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    private final RecipeTree tree;
    private final List<LayoutNode> layoutNodes = new ArrayList<>();
    private final List<CostLayout> costLayouts = new ArrayList<>();
    private final Map<ResourceLocation, Optional<IDrawable>> recipeIconCache = new HashMap<>();
    private double zoom = 1.0;
    private int panX;
    private int panY;
    private TreeNode selected;
    private boolean dragging;
    private int lastMouseX;
    private int lastMouseY;
    private String batchInput = "";
    private TreeNode dropdownNode;

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
        rebuildLayout();
        costLayouts.clear();
        renderTotals(graphics);
        renderBranches(graphics);
        renderNodes(graphics, mouseX, mouseY);
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
        graphics.drawString(font, "左键：打开 JEI 选择下级配方", x, y, 0xFF3F4C51, false);
        y += font.lineHeight + 2;
        graphics.drawString(font, "右键节点：折叠/展开    右键拖拽：移动视图", x, y, 0xFF3F4C51, false);
        y += font.lineHeight + 2;
        graphics.drawString(font, "滚轮节点：调批次数    Ctrl+滚轮：缩放", x, y, 0xFF3F4C51, false);
    }

    private void rebuildLayout() {
        layoutNodes.clear();
        int treeWidth = measure(tree.root());
        int startX = width / 2 - scale(treeWidth) / 2 + panX;
        layout(tree.root(), startX, 140 + panY, treeWidth);
    }

    private int measure(TreeNode node) {
        List<TreeNode> children = visibleChildren(node);
        if (children.isEmpty()) {
            return nodeWidth(node);
        }
        int childrenWidth = 0;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                childrenWidth += SIBLING_GAP;
            }
            childrenWidth += measure(children.get(i));
        }
        return Math.max(nodeWidth(node), childrenWidth);
    }

    private void layout(TreeNode node, int left, int y, int subtreeWidth) {
        int scaledSubtree = scale(subtreeWidth);
        int nodeW = scale(nodeWidth(node));
        int nodeH = scale(NODE_HEIGHT);
        int x = left + scaledSubtree / 2 - nodeW / 2;
        layoutNodes.add(new LayoutNode(node, x, y, nodeW, nodeH));
        int childLeft = left;
        for (TreeNode child : visibleChildren(node)) {
            int childWidth = measure(child);
            layout(child, childLeft, y + scale(LEVEL_GAP), childWidth);
            childLeft += scale(childWidth + SIBLING_GAP);
        }
    }

    private int nodeWidth(TreeNode node) {
        int contentWidth = ITEM_ICON_SIZE;
        if (node.recipe() != null) {
            contentWidth += RECIPE_ICON_SIZE + NODE_ICON_GAP;
        }
        return NODE_PADDING * 2 + contentWidth;
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
            int parentX = parent.centerX();
            int parentBottom = parent.y() + scale(NODE_HEIGHT);
            int junctionY = parentBottom + scale(NODE_HEIGHT) / 3;

            drawVLine(graphics, parentX, parentBottom, junctionY);
            renderBatchOnLine(graphics, parent.node(), parentX, parentBottom, junctionY);

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (TreeNode child : children) {
                LayoutNode childLayout = layoutFor(child);
                if (childLayout != null) {
                    minX = Math.min(minX, childLayout.centerX());
                    maxX = Math.max(maxX, childLayout.centerX());
                }
            }
            if (minX <= maxX) {
                drawHLine(graphics, minX, maxX, junctionY);
            }
            for (TreeNode child : children) {
                LayoutNode childLayout = layoutFor(child);
                if (childLayout != null) {
                    drawVLine(graphics, childLayout.centerX(), junctionY, childLayout.y());
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
        for (LayoutNode layoutNode : layoutNodes) {
            if (layoutNode.node() == node) {
                return layoutNode;
            }
        }
        return null;
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
            int contentX = layout.x() + scale(NODE_PADDING);
            int iconAreaLeft = contentX;
            if (node.recipe() != null) {
                drawRecipeIcon(graphics, node, iconAreaLeft, layout.y() + scale(7));
            }

            // 物品图标
            int itemX = contentX + scale(node.recipe() != null ? RECIPE_ICON_SIZE + NODE_ICON_GAP : 0);
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
    }

    private int nodeFill(TreeNode node) {
        if (node.cycle() || node.limited()) {
            return 0xFFF08A1A;
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

    private void renderTotals(GuiGraphics graphics) {
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
        int y = root.y() - scale(COST_ROW_HEIGHT * 2 + 8) - scale(22);
        renderCostStrip(graphics, Component.translatable("screen.jeict.total_cost"), entriesFor(costs, COST_TOTAL_BORDER), root.centerX(), y);
        renderCostStrip(graphics, Component.translatable("screen.jeict.leftovers"), entriesFor(leftovers, COST_LEFTOVER_BORDER), root.centerX(), y + scale(COST_ROW_HEIGHT + 8));
    }

    private List<CostDisplayEntry> entriesFor(Map<IngredientKey, CostEntry> entries, int border) {
        List<CostDisplayEntry> displayEntries = new ArrayList<>();
        entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.stack().getHoverName().getString()))
                .map(entry -> new CostDisplayEntry(entry.stack(), entry.count(), border))
                .forEach(displayEntries::add);
        return displayEntries;
    }

    private void renderCostStrip(GuiGraphics graphics, Component label, List<CostDisplayEntry> entries, int centerX, int y) {
        int labelWidth = scaledTextWidth(label.getString());
        int totalWidth = 0;
        for (CostDisplayEntry entry : entries) {
            totalWidth += costCellWidth(entry);
        }
        totalWidth += Math.max(0, entries.size() - 1) * scale(COST_CELL_GAP);
        int rowWidth = labelWidth + scale(8) + totalWidth;
        int x = centerX - rowWidth / 2;
        drawScaledString(graphics, label.getString(), x, y + (scale(COST_ROW_HEIGHT) - scaledFontHeight()) / 2, 0xFF2F3E43, false);
        x += labelWidth + scale(8);
        for (CostDisplayEntry entry : entries) {
            int cellWidth = costCellWidth(entry);
            int cellHeight = scale(COST_ROW_HEIGHT);
            drawBox(graphics, x, y, cellWidth, cellHeight, 0xF7F9FAFB, entry.border());
            costLayouts.add(new CostLayout(entry.stack(), x, y, cellWidth, cellHeight));

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
    }

    private int costCellWidth(CostDisplayEntry entry) {
        return scale(4 + 16 + 5) + scaledTextWidth("x" + entry.count()) + scale(6);
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
        double adjustedMultiplier = multiplier;
        if (node.recipe() != null) {
            adjustedMultiplier *= (double) node.batches() / node.baseBatches();
        }
        if (!node.expanded() || node.cycle() || node.limited() || node.recipe() == null || node.children().isEmpty()) {
            costs.compute(node.key(), (key, entry) -> CostEntry.merge(entry, node.singleStack(), effectiveAmount(node, multiplier)));
            return;
        }
        int leftoverAmount = effectiveLeftoverAmount(node, multiplier);
        if (leftoverAmount > 0) {
            leftovers.compute(node.key(), (key, entry) -> CostEntry.merge(entry, node.singleStack(), leftoverAmount));
        }
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

    private int effectiveLeftoverAmount(TreeNode node, double multiplier) {
        if (node.recipe() == null) {
            return 0;
        }
        int produced = (int) Math.ceil(node.batches() * node.outputPerBatch() * multiplier);
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
            childMultiplier *= (double) current.batches() / current.baseBatches();
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
        for (LayoutNode layout : layoutNodes) {
            if (!layout.contains(mouseX, mouseY)) {
                continue;
            }
            TreeNode node = layout.node();
            List<Component> lines = node.displayStack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL);
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
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
        return Math.max(2, scale(2));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dropdownNode != null && clickAlternativeDropdown((int) mouseX, (int) mouseY)) {
            return true;
        }
        if (button == 0) {
            for (CostLayout layout : costLayouts) {
                if (layout.contains((int) mouseX, (int) mouseY)) {
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
                    selected.toggleExpanded();
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
        for (LayoutNode layout : layoutNodes) {
            if (layout.contains((int) mouseX, (int) mouseY) && canEditBatches(layout.node())) {
                int step = hasShiftDown() ? 10 : 1;
                layout.node().batches(layout.node().batches() + (delta > 0 ? step : -step));
                batchInput = "";
                selected = layout.node();
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

    private boolean canEditBatches(TreeNode node) {
        return node == tree.root() && node.recipe() != null;
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
        Map<IngredientKey, ItemStack> displayStacks = new HashMap<>();
        Map<IngredientKey, Integer> counts = new HashMap<>();
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
        }
        if (counts.isEmpty()) {
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
            applyRecipeSelection(node, recipe, output, displayStacks, counts);
        }
        recipeIconCache.clear();
        batchInput = "";
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
    }

    private void applyRecipeSelection(TreeNode node, Object recipe, ItemStack output, Map<IngredientKey, ItemStack> displayStacks, Map<IngredientKey, Integer> counts) {
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
        for (Map.Entry<IngredientKey, Integer> entry : counts.entrySet()) {
            ItemStack childStack = displayStacks.get(entry.getKey()).copy();
            int amount = entry.getValue() * batches;
            TreeNode child = cache
                    .map(graph -> graph.createNode(childStack, amount, node.depth() + 1))
                    .orElseGet(() -> new TreeNode(entry.getKey(), childStack, amount, node.depth() + 1));
            node.children().add(child);
        }
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

    private record LayoutNode(TreeNode node, int x, int y, int width, int height) {
        int centerX() {
            return x + width / 2;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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

    private record CostDisplayEntry(ItemStack stack, int count, int border) {
    }

    private record CostLayout(ItemStack stack, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record JeiRecipeMatch<T>(IRecipeCategory<T> category, T recipe) {
        private void show(IRecipesGui recipesGui, List<IFocus<?>> focuses) {
            recipesGui.showRecipes(category, List.of(recipe), focuses);
        }
    }
}
