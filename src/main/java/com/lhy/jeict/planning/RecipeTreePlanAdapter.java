package com.lhy.jeict.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.api.CraftingTreeBackend;
import com.lhy.jeict.api.CraftingTreeBackends;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeOutputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.util.IngredientIdentityUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

/** Converts the currently selected mutable UI tree into an immutable, thread-safe planning graph. */
public final class RecipeTreePlanAdapter {
    private RecipeTreePlanAdapter() {
    }

    public static PlanTarget target(String project, RecipeTreeNodeViewModel root, long amount) {
        IIngredientManager manager = manager();
        Map<RecipeTreeNodeViewModel, PlanNode> cache = new IdentityHashMap<>();
        PlanNode planRoot = adaptNode(root, manager, cache);
        MaterialKey output = keyOf(manager, root.recipe().primaryOutputIngredient(), root.recipe().primaryOutput());
        return new PlanTarget(project, planRoot, output, amount);
    }

    public static List<PlanTarget> targets(Map<String, RecipeTreeNodeViewModel> projects, Map<String, Long> amounts) {
        List<PlanTarget> targets = new ArrayList<>();
        for (Map.Entry<String, RecipeTreeNodeViewModel> entry : projects.entrySet()) {
            targets.add(target(entry.getKey(), entry.getValue(), amounts.getOrDefault(entry.getKey(), 1L)));
        }
        return List.copyOf(targets);
    }

    private static PlanNode adaptNode(RecipeTreeNodeViewModel node, @Nullable IIngredientManager manager,
            Map<RecipeTreeNodeViewModel, PlanNode> cache) {
        PlanNode cached = cache.get(node);
        if (cached != null) return cached;
        RecipeTreeRecipeViewModel recipeView = node.recipe();
        List<PlanOutput> outputs = new ArrayList<>();
        for (RecipeTreeOutputViewModel output : recipeView.outputs()) {
            outputs.add(new PlanOutput(materialOf(manager, output.ingredient(), output.itemStack()), output.amount(),
                    output.chance(), output.primary()));
        }
        if (outputs.isEmpty()) {
            outputs.add(new PlanOutput(materialOf(manager, recipeView.primaryOutputIngredient(), recipeView.primaryOutput()),
                    recipeView.primaryOutputAmount(), 1D, true));
        }

        CraftingTreeBackend backend = CraftingTreeBackends.get();
        List<PlanInput> inputs = new ArrayList<>();
        Map<MaterialKey, RecipeTreeNodeViewModel> childViews = new LinkedHashMap<>();
        Map<Integer, RecipeTreeNodeViewModel> inputChildViews = new LinkedHashMap<>();
        int inputIndex = 0;
        for (RecipeTreeInputViewModel input : recipeView.inputs()) {
            List<PlanMaterial> alternatives = new ArrayList<>();
            for (RecipeTreeInputViewModel.DisplayOption option : input.orderedDisplayOptions()) {
                alternatives.add(materialOf(manager, option.typedIngredient(), option.itemStack(), option.label()));
            }
            if (alternatives.isEmpty()) {
                for (ItemStack stack : input.orderedAlternativesView()) {
                    alternatives.add(materialOf(manager, null, stack, stack.getHoverName().getString()));
                }
            }
            boolean consumed = input.consumed() && (backend == null || !backend.isReusableInput(recipeView, inputIndex));
            inputs.add(new PlanInput(alternatives, input.longAmount(), consumed, 0));
            if (input.child() != null) {
                RecipeTreeNodeViewModel child = input.child();
                MaterialKey childOutput = keyOf(manager, child.recipe().primaryOutputIngredient(), child.recipe().primaryOutput());
                childViews.putIfAbsent(childOutput, child);
                inputChildViews.put(inputIndex, child);
            }
            inputIndex++;
        }
        String machine = backend == null
                ? (recipeView.subtitle() == null ? "crafting" : recipeView.subtitle().getString())
                : backend.machineId(recipeView);
        PlanRecipe planRecipe = new PlanRecipe(recipeView.stableIdentity(), recipeView.title().getString(), machine, inputs, outputs);
        Map<MaterialKey, PlanNode> producers = new HashMap<>();
        Map<Integer, PlanNode> inputProducers = new HashMap<>();
        // Insert a placeholder first to break accidental object cycles. The final immutable graph still reports cycles in the solver.
        PlanNode placeholder = new PlanNode(planRecipe);
        cache.put(node, placeholder);
        childViews.forEach((key, child) -> producers.put(key, adaptNode(child, manager, cache)));
        inputChildViews.forEach((index, child) -> inputProducers.put(index, adaptNode(child, manager, cache)));
        PlanNode result = new PlanNode(planRecipe, producers, inputProducers);
        cache.put(node, result);
        return result;
    }

    private static PlanMaterial materialOf(@Nullable IIngredientManager manager,
            @Nullable ITypedIngredient<?> typed, ItemStack stack) {
        return materialOf(manager, typed, stack, stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString());
    }

    private static PlanMaterial materialOf(@Nullable IIngredientManager manager,
            @Nullable ITypedIngredient<?> typed, ItemStack stack, String label) {
        MaterialKey key = keyOf(manager, typed, stack);
        return new PlanMaterial(key, label);
    }

    private static MaterialKey keyOf(@Nullable IIngredientManager manager,
            @Nullable ITypedIngredient<?> typed, ItemStack stack) {
        if (manager != null && typed != null) return IngredientIdentityUtil.keyOf(manager, typed);
        return MaterialKey.of(IngredientIdentityUtil.fallbackSignature(typed, stack));
    }

    private static @Nullable IIngredientManager manager() {
        var runtime = JeiCraftingTreePlugin.getJeiRuntime();
        return runtime == null ? null : runtime.getIngredientManager();
    }
}
