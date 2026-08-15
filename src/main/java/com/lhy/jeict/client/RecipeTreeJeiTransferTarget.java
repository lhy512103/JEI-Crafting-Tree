package com.lhy.jeict.client;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

public interface RecipeTreeJeiTransferTarget {
    void jeict$applyJeiRecipe(Object recipe, IRecipeSlotsView recipeSlots);
}
