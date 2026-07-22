package com.lhy.jeict.recipe_tree;

import java.util.ArrayList;
import java.util.List;
import com.lhy.jeict.compat.JustEnoughCharactersCompat;

/** Search/filter projection shared by graph, merged layers, and execution checklist views. */
public final class RecipeTreeSearchIndex {
    private RecipeTreeSearchIndex() {
    }

    public static boolean matches(RecipeTreeRecipeViewModel recipe, String query) {
        if (query == null || query.isBlank()) return true;
        return JustEnoughCharactersCompat.contains(recipe.title().getString(), query)
                || JustEnoughCharactersCompat.contains(recipe.stableIdentity(), query)
                || recipe.inputs().stream()
                        .anyMatch(input -> JustEnoughCharactersCompat.contains(input.displayName(), query));
    }

    public static List<RecipeTreeNodeViewModel> matchingNodes(RecipeTreeNodeViewModel root, String query) {
        List<RecipeTreeNodeViewModel> result = new ArrayList<>();
        collect(root, query, result);
        return List.copyOf(result);
    }

    private static void collect(RecipeTreeNodeViewModel node, String query, List<RecipeTreeNodeViewModel> result) {
        if (matches(node.recipe(), query)) result.add(node);
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            if (input.child() != null) collect(input.child(), query, result);
        }
    }
}
