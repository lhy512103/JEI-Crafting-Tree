package com.lhy.jeict.recipe_tree;

import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.api.PatternEncodingDraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private final RecipeTreeNodeViewModel initialRoot;
    private final @Nullable Screen returnScreen;
    private final RecipeTreeProjectManager projects;
    /** Pattern edits belong to this tree context, not to one temporary screen instance. */
    private final Map<String, PatternEncodingDraft> patternDrafts = new HashMap<>();
    private final Map<String, RecipeTreeRecipeViewModel> patternDraftSourceRecipes = new HashMap<>();
    private final Set<String> modifiedPatternRecipeKeys = new HashSet<>();
    private final Set<RecipeTreeNodeViewModel> modifiedPatternNodes =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean disableExistingPatternExpansion = true;

    public RecipeTreeRootContext(RecipeTreeNodeViewModel root, @Nullable Screen returnScreen) {
        this.initialRoot = root;
        this.returnScreen = returnScreen;
        this.projects = new RecipeTreeProjectManager(root);
    }

    public RecipeTreeNodeViewModel root() {
        RecipeTreeNodeViewModel active = projects.activeRoot();
        return active == null ? initialRoot : active;
    }

    public @Nullable Screen returnScreen() {
        return returnScreen;
    }

    public RecipeTreeProjectManager projects() {
        return projects;
    }

    public Map<String, PatternEncodingDraft> patternDrafts() {
        return patternDrafts;
    }

    public Map<String, RecipeTreeRecipeViewModel> patternDraftSourceRecipes() {
        return patternDraftSourceRecipes;
    }

    public Set<String> modifiedPatternRecipeKeys() {
        return modifiedPatternRecipeKeys;
    }

    public Set<RecipeTreeNodeViewModel> modifiedPatternNodes() {
        return modifiedPatternNodes;
    }

    public Component title() {
        return root().recipe().title();
    }

    public List<RequestedIngredient> collectRequestedIngredients() {
        Map<String, RecipeTreeNodeViewModel.LeafAccumulator> merged = new LinkedHashMap<>();
        root().collectLeavesMerged(1, merged);
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
        collectSurplusIngredients(root(), Math.max(1, rootCrafts), merged);
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
        collectSelectedRecipes(root(), raw, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
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
        if (RecipeTreeConfig.REMEMBER_SELECTIONS.get()) RecipeTreeClientMemory.rememberSelection(signature, recipe);
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        return RecipeTreeConfig.REMEMBER_SELECTIONS.get()
                ? RecipeTreeClientMemory.getRememberedSelection(signature) : null;
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature,
            @Nullable ITypedIngredient<?> outputFocus) {
        return RecipeTreeConfig.REMEMBER_SELECTIONS.get()
                ? RecipeTreeClientMemory.getRememberedSelection(signature, outputFocus) : null;
    }

    public void forgetSelection(String signature) {
        RecipeTreeClientMemory.forgetSelection(signature);
    }


    public void rememberSelection(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String legacySignature, RecipeTreeRecipeViewModel recipe) {
        if (!RecipeTreeConfig.REMEMBER_SELECTIONS.get()) return;
        String scoped = com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature);
        RecipeTreeClientMemory.rememberSelection(scoped, recipe);
        // 材料级兜底：位置键只在该父配方/槽位命中，同一材料出现在树的其他位置时读取会落空。
        // 读取侧（getRememberedSelection 的 legacy 降级）会先查位置键、未命中再按材料签名兜底，
        // 因此这里始终把最新选择同步一份到材料级键，让新展开的分支也能自动展开。
        RecipeTreeClientMemory.rememberSelection(legacySignature, recipe);
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(RecipeTreeNodeViewModel parent, int inputIndex,
            RecipeTreeInputViewModel input, String legacySignature, @Nullable ITypedIngredient<?> outputFocus) {
        if (!RecipeTreeConfig.REMEMBER_SELECTIONS.get()) return null;
        String scoped = com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature);
        RecipeTreeRecipeViewModel selected = RecipeTreeClientMemory.getRememberedSelection(scoped, outputFocus);
        if (selected != null) return selected;
        // Legacy fallback transparently migrates the old unscoped properties entry on first successful read.
        selected = RecipeTreeClientMemory.getRememberedSelection(legacySignature, outputFocus);
        if (selected != null) RecipeTreeClientMemory.rememberSelection(scoped, selected);
        return selected;
    }

    public void forgetSelection(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String legacySignature) {
        RecipeTreeClientMemory.forgetSelection(
                com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature));
        RecipeTreeClientMemory.forgetSelection(legacySignature);
    }

    public void rememberCollapsed(String signature) {
        if (RecipeTreeConfig.REMEMBER_SELECTIONS.get()) RecipeTreeClientMemory.rememberCollapsed(signature);
    }

    public void forgetCollapsed(String signature) {
        RecipeTreeClientMemory.forgetCollapsed(signature);
    }

    public boolean isCollapsed(String signature) {
        return RecipeTreeConfig.REMEMBER_SELECTIONS.get() && RecipeTreeClientMemory.isCollapsed(signature);
    }

    public void rememberCollapsed(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String legacySignature) {
        if (!RecipeTreeConfig.REMEMBER_SELECTIONS.get()) return;
        RecipeTreeClientMemory.rememberCollapsed(
                com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature));
    }

    public void forgetCollapsed(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String legacySignature) {
        RecipeTreeClientMemory.forgetCollapsed(
                com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature));
        RecipeTreeClientMemory.forgetCollapsed(legacySignature);
    }

    public boolean isCollapsed(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String legacySignature) {
        if (!RecipeTreeConfig.REMEMBER_SELECTIONS.get()) return false;
        String scoped = com.lhy.jeict.client.RecipeTreeMemoryKey.of(parent, inputIndex, input, legacySignature);
        if (RecipeTreeClientMemory.isCollapsed(scoped)) return true;
        if (!RecipeTreeClientMemory.isCollapsed(legacySignature)) return false;
        RecipeTreeClientMemory.rememberCollapsed(scoped);
        return true;
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
