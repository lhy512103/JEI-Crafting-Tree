package com.lhy.jeict.jei;

import com.lhy.jeict.client.JeiStyleButtons;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Bookmark-bar recipe-tree button; uses JEI IconButton chrome/state like the native buttons. */
public final class BookmarkTreeButtonController implements IIconButtonController {
    @Override
    public void initState(IButtonState state) {
        updateState(state);
    }

    @Override
    public void updateState(IButtonState state) {
        boolean hasTree = RecipeTreeOpenHelper.hasLastWorkspace();
        IDrawable icon = JeiStyleButtons.icon(!hasTree);
        if (icon != null) {
            state.setIcon(icon);
        }
        state.setVisible(true);
        state.setActive(true);
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (input.isSimulate()) {
            return true;
        }
        if (RecipeTreeOpenHelper.hasLastWorkspace()) {
            RecipeTreeOpenHelper.openLastWorkspace(Minecraft.getInstance().screen);
        } else {
            RecipeTreeOpenHelper.openJeiForNewTree(Minecraft.getInstance().screen);
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        boolean hasTree = RecipeTreeOpenHelper.hasLastWorkspace();
        tooltip.add(Component.translatable(hasTree
                ? "gui.jeict.recipe_tree.jei_shortcut_open_tooltip"
                : "gui.jeict.recipe_tree.jei_shortcut_add_tooltip"));
    }
}
