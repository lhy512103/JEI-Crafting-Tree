package com.lhy.jeict.api;

import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;

/** A recipe and the exact editable draft that must be encoded for it. */
public record PatternEncodingRequest(RecipeTreeRecipeViewModel recipe, PatternEncodingDraft draft) {
}
