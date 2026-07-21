package com.lhy.jeict.recipe_tree;

import java.util.ArrayList;
import java.util.List;

/** Creates an independent mutable copy of a selected recipe tree. */
public final class RecipeTreeCopies {
    private RecipeTreeCopies() {}

    public static RecipeTreeNodeViewModel deepCopy(RecipeTreeNodeViewModel source) {
        return copyNode(source, null);
    }

    private static RecipeTreeNodeViewModel copyNode(RecipeTreeNodeViewModel source, RecipeTreeNodeViewModel parent) {
        List<RecipeTreeInputViewModel> inputs = new ArrayList<>();
        for (RecipeTreeInputViewModel input : source.recipe().inputs()) {
            RecipeTreeInputViewModel copied = new RecipeTreeInputViewModel(input.requestedIngredientView(),
                    input.displayOptions(), input.longAmount(), input.amountText(), input.consumed());
            copied.selectAlternative(input.selectedAlternativeIndex());
            inputs.add(copied);
        }
        RecipeTreeRecipeViewModel recipe = source.recipe();
        RecipeTreeRecipeViewModel copiedRecipe = new RecipeTreeRecipeViewModel(recipe.primaryOutputIngredient(),
                recipe.primaryOutput(), recipe.primaryOutputAmount(), recipe.outputs(), recipe.title(), recipe.subtitle(),
                recipe.subtitleIcon(), recipe.recipeId(), inputs);
        RecipeTreeNodeViewModel result = new RecipeTreeNodeViewModel(copiedRecipe, parent);
        for (int i = 0; i < inputs.size(); i++) {
            RecipeTreeNodeViewModel child = source.recipe().inputs().get(i).child();
            if (child != null) inputs.get(i).setChild(copyNode(child, result));
        }
        return result;
    }
}
