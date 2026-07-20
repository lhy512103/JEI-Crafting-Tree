package com.lhy.jeict.recipe_tree;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.client.RecipeTreeClientMemory;

import mezz.jei.api.ingredients.ITypedIngredient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class RecipeTreeRootContext {
    private final RecipeTreeNodeViewModel root;
    private final @Nullable Screen returnScreen;
    private boolean disableExistingPatternExpansion = true;

    public RecipeTreeRootContext(RecipeTreeNodeViewModel root, @Nullable Screen returnScreen) {
        this.root = root;
        this.returnScreen = returnScreen;
    }

    public RecipeTreeNodeViewModel root() {
        return root;
    }

    public @Nullable Screen returnScreen() {
        return returnScreen;
    }

    public Component title() {
        return root.recipe().title();
    }

    public List<RequestedIngredient> collectRequestedIngredients() {
        Map<String, RecipeTreeNodeViewModel.LeafAccumulator> merged = new LinkedHashMap<>();
        root.collectLeavesMerged(1, merged);
        List<RequestedIngredient> requestedIngredients = new ArrayList<>(merged.size());
        for (RecipeTreeNodeViewModel.LeafAccumulator accumulator : merged.values()) {
            requestedIngredients.add(accumulator.toRequestedIngredient());
        }
        return List.copyOf(requestedIngredients);
    }

    public List<RequestedIngredient> collectSurplusIngredients() {
        return collectSurplusIngredients(1);
    }

    public List<RequestedIngredient> collectSurplusIngredients(int rootCrafts) {
        Map<String, RecipeTreeNodeViewModel.LeafAccumulator> merged = new LinkedHashMap<>();
        collectSurplusIngredients(root, Math.max(1, rootCrafts), merged);
        List<RequestedIngredient> surplus = new ArrayList<>(merged.size());
        for (RecipeTreeNodeViewModel.LeafAccumulator accumulator : merged.values()) {
            surplus.add(accumulator.toRequestedIngredient());
        }
        return List.copyOf(surplus);
    }

    private void collectSurplusIngredients(RecipeTreeNodeViewModel node, int crafts,
            Map<String, RecipeTreeNodeViewModel.LeafAccumulator> merged) {
        Map<RecipeTreeNodeViewModel, Integer> childRequirements = new IdentityHashMap<>();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                childRequirements.merge(child, safeMultiply(crafts, input.amount()), RecipeTreeRootContext::safeAdd);
            }
        }
        for (Map.Entry<RecipeTreeNodeViewModel, Integer> entry : childRequirements.entrySet()) {
            RecipeTreeNodeViewModel child = entry.getKey();
            int required = Math.max(1, entry.getValue());
            int outputCount = Math.max(1, child.recipe().primaryOutputCount());
            int childCrafts = ceilDiv(required, outputCount);
            int extra = Math.max(0, safeMultiply(childCrafts, outputCount) - required);
            ItemStack output = child.recipe().primaryOutput();
            if (extra > 0 && !output.isEmpty()) {
                String key = output.getItem().toString();
                RecipeTreeNodeViewModel.LeafAccumulator accumulator = merged.get(key);
                if (accumulator == null) {
                    merged.put(key, new RecipeTreeNodeViewModel.LeafAccumulator(List.of(output.copyWithCount(1)), extra));
                } else {
                    accumulator.add(extra);
                }
            }
            collectSurplusIngredients(child, childCrafts, merged);
        }
    }

    public List<RecipeTreeRecipeViewModel> collectSelectedRecipes() {
        List<RecipeTreeRecipeViewModel> raw = new ArrayList<>();
        collectSelectedRecipes(root, raw, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        List<RecipeTreeRecipeViewModel> unique = new ArrayList<>();
        List<RecipeTreeRecipeViewModel> recipesWithoutId = new ArrayList<>();
        Set<net.minecraft.resources.ResourceLocation> seenRecipeIds = new java.util.HashSet<>();
        outer: for (RecipeTreeRecipeViewModel candidate : raw) {
            net.minecraft.resources.ResourceLocation recipeId = candidate.recipeId();
            if (recipeId != null) {
                if (seenRecipeIds.add(recipeId)) {
                    unique.add(candidate);
                }
                continue;
            }
            for (RecipeTreeRecipeViewModel existing : recipesWithoutId) {
                if (existing.sameRecipeAs(candidate)) {
                    continue outer;
                }
            }
            recipesWithoutId.add(candidate);
            unique.add(candidate);
        }
        return List.copyOf(unique);
    }

    public void rememberSelection(String signature, RecipeTreeRecipeViewModel recipe) {
        RecipeTreeClientMemory.rememberSelection(signature, recipe);
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        return RecipeTreeClientMemory.getRememberedSelection(signature);
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature,
            @Nullable ITypedIngredient<?> outputFocus) {
        return RecipeTreeClientMemory.getRememberedSelection(signature, outputFocus);
    }

    public void forgetSelection(String signature) {
        RecipeTreeClientMemory.forgetSelection(signature);
    }

    public void rememberCollapsed(String signature) {
        RecipeTreeClientMemory.rememberCollapsed(signature);
    }

    public void forgetCollapsed(String signature) {
        RecipeTreeClientMemory.forgetCollapsed(signature);
    }

    public boolean isCollapsed(String signature) {
        return RecipeTreeClientMemory.isCollapsed(signature);
    }

    public boolean disableExistingPatternExpansion() {
        return disableExistingPatternExpansion;
    }

    public void setDisableExistingPatternExpansion(boolean disableExistingPatternExpansion) {
        this.disableExistingPatternExpansion = disableExistingPatternExpansion;
    }

    private static int safeMultiply(int left, int right) {
        long value = (long) left * (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int safeAdd(int left, int right) {
        long value = (long) left + (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            return numerator;
        }
        long value = ((long) numerator + (long) denominator - 1L) / (long) denominator;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private void collectSelectedRecipes(RecipeTreeNodeViewModel node, List<RecipeTreeRecipeViewModel> recipes,
            Set<RecipeTreeNodeViewModel> visitedNodes) {
        if (!visitedNodes.add(node)) {
            return;
        }
        recipes.add(node.recipe());
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                collectSelectedRecipes(child, recipes, visitedNodes);
            }
        }
    }
}
