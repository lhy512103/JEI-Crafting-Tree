package com.lhy.jeict.jei;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;

/**
 * JEI 配方页侧栏的「打开配方树」箭头按钮。左键直接打开配方树。
 *
 * <p>当 AE2 Utility 已安装且其自身 JEI 编码箭头可见时，本按钮隐藏，避免出现两个箭头；
 * AE2 Utility 箭头不可见（如未开终端且身上没有无线样板终端）时，本按钮接管显示。
 * AE2 Utility 侧通过 {@code com.lhy.ae2utility.api.Ae2UtilityClientApi#isJeiPatternEncodingAvailable()}
 * 提供与自身箭头同源的可见性判定；反射失败或旧版本无此 API 时按「可用」处理（保持隐藏，
 * 避免在 AE2 Utility 编码箭头存在时重复显示）。
 */
public class OpenTreeButtonController implements IIconButtonController {
    private static final int BG_COLOR = 0x804545FF;
    private static final String AE2U_CLIENT_API = "com.lhy.ae2utility.api.Ae2UtilityClientApi";
    private static final String AE2U_QUERY_METHOD = "isJeiPatternEncodingAvailable";
    private static boolean ae2uQueryResolved;
    private static @org.jetbrains.annotations.Nullable Method ae2uQueryMethod;

    private final IRecipeLayoutDrawable<?> recipeLayout;

    private final IDrawable arrowIcon = new IDrawable() {
        @Override
        public int getWidth() {
            return 10;
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.fill(x - 1, y - 1, x + 11, y + 11, BG_COLOR);
            // 自绘向上箭头
            int cx = x + 5;
            graphics.fill(cx - 1, y + 1, cx + 1, y + 9, 0xFFFFFFFF);
            graphics.fill(cx - 3, y + 4, cx + 3, y + 6, 0xFFFFFFFF);
            graphics.fill(cx - 2, y + 2, cx + 2, y + 3, 0xFFFFFFFF);
        }
    };

    public OpenTreeButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
    }

    /** AE2 Utility 编码箭头当前是否可见；探测失败或旧版本按可见处理，避免双箭头。 */
    private static boolean ae2UtilityEncodeArrowVisible() {
        if (!ModList.get().isLoaded("ae2utility")) {
            return false;
        }
        if (!ae2uQueryResolved) {
            ae2uQueryResolved = true;
            try {
                Class<?> api = Class.forName(AE2U_CLIENT_API);
                ae2uQueryMethod = api.getMethod(AE2U_QUERY_METHOD);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                ae2uQueryMethod = null;
            }
        }
        Method query = ae2uQueryMethod;
        if (query == null) {
            // 旧版 AE2 Utility 无此查询 API：保持隐藏，避免双箭头。
            return true;
        }
        try {
            return Boolean.TRUE.equals(query.invoke(null));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return true;
        }
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(arrowIcon);
    }

    @Override
    public void updateState(IButtonState state) {
        boolean visible = !ae2UtilityEncodeArrowVisible();
        state.setVisible(visible);
        state.setActive(visible);
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (ae2UtilityEncodeArrowVisible()) {
            return false;
        }
        if (input.isSimulate()) {
            return true;
        }
        RecipeTreeOpenHelper.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("jei.tooltip.jeict.open_tree_button"));
    }
}
