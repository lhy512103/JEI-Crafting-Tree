package com.lhy.jeict.recipe_tree;

import java.util.List;

import com.lhy.jeict.jei.RecipeTreeJeiLookup;

public final class RecipeTreeExpansionResolver {
    private RecipeTreeExpansionResolver() {
    }

    public static List<RecipeTreeRecipeViewModel> resolveCandidates(RecipeTreeInputViewModel input) {
        return RecipeTreeJeiLookup.findRecipesByOutput(input.displayIngredient());
    }
}
