package com.lhy.jeict.jei;

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
 * <p>当 AE2 Utility 已安装时，本按钮隐藏：改由 AE2 Utility 自身的编码箭头通过
 * Alt+左键调用 {@link com.lhy.jeict.api.JeiCraftingTreeApi} 打开配方树，避免出现两个箭头。
 */
public class OpenTreeButtonController implements IIconButtonController {
    private static final int BG_COLOR = 0x804545FF;

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

    private static boolean hiddenByAe2Utility() {
        return ModList.get().isLoaded("ae2utility");
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(arrowIcon);
    }

    @Override
    public void updateState(IButtonState state) {
        boolean visible = !hiddenByAe2Utility();
        state.setVisible(visible);
        state.setActive(visible);
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (hiddenByAe2Utility()) {
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
