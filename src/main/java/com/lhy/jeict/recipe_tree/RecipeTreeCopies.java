package com.lhy.jeict.recipe_tree;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Creates an independent mutable copy of a selected recipe tree while preserving shared DAG branches. */
public final class RecipeTreeCopies {
    private RecipeTreeCopies() {}

    public static RecipeTreeNodeViewModel deepCopy(RecipeTreeNodeViewModel source) {
        return copyNode(source, null, new IdentityHashMap<>());
    }

    private static RecipeTreeNodeViewModel copyNode(RecipeTreeNodeViewModel source,
            RecipeTreeNodeViewModel parent,
            Map<RecipeTreeNodeViewModel, RecipeTreeNodeViewModel> copies) {
        RecipeTreeNodeViewModel existing = copies.get(source);
        if (existing != null) {
            return existing;
        }

        List<RecipeTreeInputViewModel> inputs = new ArrayList<>();
        for (RecipeTreeInputViewModel input : source.recipe().inputs()) {
            RecipeTreeInputViewModel copied = new RecipeTreeInputViewModel(input.requestedIngredientView(),
                    input.displayOptions(), input.longAmount(), input.amountText(), input.consumed(), input.patternSlotIndex());
            copied.selectAlternative(input.selectedAlternativeIndex());
            inputs.add(copied);
        }
        RecipeTreeRecipeViewModel recipe = source.recipe();
        RecipeTreeRecipeViewModel copiedRecipe = new RecipeTreeRecipeViewModel(recipe.primaryOutputIngredient(),
                recipe.primaryOutput(), recipe.primaryOutputAmount(), recipe.outputs(), recipe.title(), recipe.subtitle(),
                recipe.subtitleIcon(), recipe.recipeId(), inputs);
        RecipeTreeNodeViewModel result = new RecipeTreeNodeViewModel(copiedRecipe, parent);
        result.setPatternModified(source.isPatternModified());

        // Register before descending. A recipe tree normally has no cycles, but this also prevents malformed
        // data from recursing forever and, critically, keeps repeated 3x3 inputs pointing at one copied child.
        copies.put(source, result);
        for (int i = 0; i < inputs.size(); i++) {
            RecipeTreeNodeViewModel child = source.recipe().inputs().get(i).child();
            if (child != null) {
                inputs.get(i).setChild(copyNode(child, result, copies));
            }
        }
        return result;
    }
}
