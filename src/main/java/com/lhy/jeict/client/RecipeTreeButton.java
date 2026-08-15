package com.lhy.jeict.client;

import com.lhy.jeict.tree.RecipeGraphCache;
import com.lhy.jeict.tree.RecipeTree;
import com.lhy.jeict.client.screen.RecipeTreeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 屏幕左下角的"打开配方树"入口按钮。
 *
 * <p>J EI 的 bookmark 按钮没有公开 API 让外部加按钮到它附近，要严格贴在 bookmark 按钮上方需要 mixin/反射。
 * 本类使用 JEI 公开的 {@code IGlobalGuiHandler.getGuiExtraAreas()} 让 JEI 物品列表自动避让，
 * 同时通过 Forge 公开的 {@code ScreenEvent} 处理点击和渲染，零反射、零 mixin。
 *
 * <p>按钮位置固定在屏幕左下角内边距 8px 的 20×20 方块，渲染一个向上箭头，
 * 与 JEI bookmark 按钮风格统一（描边 + 居中符号）。点击时如果玩家手上有物品，
 * 就用主手或副手物品打开 {@link RecipeTreeScreen}。
 */
public final class RecipeTreeButton {
    /** 按钮尺寸，跟 JEI bookmark 按钮一致（20×20）。 */
    public static final int SIZE = 20;
    /** 屏幕边距。 */
    public static final int MARGIN = 8;

    private static final RecipeTreeButton INSTANCE = new RecipeTreeButton();

    private Rect2i currentRect;
    private boolean visible;
    private boolean hovered;

    private RecipeTreeButton() {
    }

    public static RecipeTreeButton get() {
        return INSTANCE;
    }

    /**
     * 每帧调用，更新按钮可见性与坐标。不依赖鼠标位置——可在 JEI 的 layout 阶段安全调用。
     */
    public void updateVisibility() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.screen == null) {
            visible = false;
            return;
        }
        if (com.lhy.jeict.integration.JeiRuntimeAccess.get().isEmpty()) {
            visible = false;
            return;
        }
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = MARGIN;
        int y = screenHeight - MARGIN - SIZE;
        currentRect = new Rect2i(x, y, SIZE, SIZE);
        visible = true;
    }

    /** 由 {@code JeiTreePlugin} 注册的 IGlobalGuiHandler 在每帧 layout 阶段调用，告诉 JEI 避开按钮区域。 */
    public Optional<Rect2i> currentArea() {
        if (!visible || currentRect == null) {
            return Optional.empty();
        }
        return Optional.of(currentRect);
    }

    /** 当前是否显示（依赖 JEI overlay 可见）。 */
    public boolean isVisible() {
        return visible;
    }

    public Rect2i rect() {
        return currentRect;
    }

    public boolean isHovered() {
        return hovered;
    }

    public void updateHover(int mouseX, int mouseY) {
        if (!visible || currentRect == null) {
            hovered = false;
            return;
        }
        int x = currentRect.getX();
        int y = currentRect.getY();
        hovered = mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /** 由 Forge {@code ScreenEvent.Render.Post} 触发的渲染入口。 */
    public void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!visible || currentRect == null) {
            return;
        }
        int x = currentRect.getX();
        int y = currentRect.getY();
        int w = currentRect.getWidth();
        int h = currentRect.getHeight();
        // 背景框：跟 JEI bookmark 按钮一样的暗框 + 悬停高亮
        int border = hovered ? 0xFFFFFFFF : 0xFF6B777C;
        int fill = hovered ? 0xFFE8ECEF : 0xFF4B555A;
        graphics.fill(x, y, x + w, y + h, fill);
        // 描边
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        // 向上箭头（白色三角形，底宽顶尖，居中）
        int arrowColor = 0xFFFFFFFF;
        int midX = x + w / 2;
        int topY = y + 5;
        int bottomY = y + h - 5;
        int rows = bottomY - topY;
        for (int row = 0; row < rows; row++) {
            // row=0 时顶部最窄（半宽 1），row 增加时半宽线性增大
            int halfWidth = Math.max(1, (row + 2) / 2);
            graphics.fill(midX - halfWidth, topY + row, midX + halfWidth + 1, topY + row + 1, arrowColor);
        }
    }

    /** 处理鼠标点击：命中时打开配方树。 */
    public boolean handleClick(Minecraft minecraft, double mouseX, double mouseY) {
        if (!visible || currentRect == null) {
            return false;
        }
        int x = currentRect.getX();
        int y = currentRect.getY();
        if (!(mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE)) {
            return false;
        }
        openRecipeTree(minecraft);
        return true;
    }

    /** 悬停 tooltip，由 Forge Render 事件随鼠标位置触发。 */
    public void renderTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        if (!visible || !hovered) {
            return;
        }
        graphics.renderTooltip(
                minecraft.font,
                Component.translatable("screen.jeict.open_tree_button.tooltip"),
                mouseX,
                mouseY);
    }

    private void openRecipeTree(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ItemStack goal = minecraft.player.getMainHandItem();
        if (goal.isEmpty()) {
            goal = minecraft.player.getOffhandItem();
        }
        if (goal.isEmpty()) {
            // 没有目标物品时给玩家文字提示，但不做任何屏幕切换
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("screen.jeict.open_tree_button.need_item"),
                        true);
            }
            return;
        }
        ItemStack finalGoal = goal.copy();
        Optional<RecipeTree> tree = RecipeGraphCache.get(minecraft)
                .flatMap(cache -> cache.createTree(finalGoal));
        if (tree.isPresent()) {
            minecraft.setScreen(new RecipeTreeScreen(tree.get()));
        }
    }
}
