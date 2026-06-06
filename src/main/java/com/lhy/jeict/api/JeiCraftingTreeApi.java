package com.lhy.jeict.api;

import com.lhy.jeict.jei.RecipeTreeOpenHelper;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.screens.Screen;

/**
 * 供外部模组（如 AE2 Utility）调用的公共入口：从 JEI 配方页打开配方树总览界面。
 */
public final class JeiCraftingTreeApi {
    private JeiCraftingTreeApi() {
    }

    public static void openFromLayout(IRecipeLayoutDrawable<?> recipeLayout, Screen returnScreen) {
        RecipeTreeOpenHelper.openFromLayout(recipeLayout, returnScreen);
    }

    public static void open(Object recipe, IRecipeSlotsView recipeSlots, Screen returnScreen) {
        RecipeTreeOpenHelper.open(recipe, recipeSlots, returnScreen);
    }
}
