package com.lhy.jeict.tree;

import com.lhy.jeict.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RecipeGraphCache {
    private static RecipeGraphCache current;

    private final Map<IngredientKey, List<RecipeNode>> recipesByOutput = new HashMap<>();

    private RecipeGraphCache(Minecraft minecraft) {
        RegistryAccess registries = minecraft.level.registryAccess();
        for (Recipe<?> recipe : minecraft.level.getRecipeManager().getRecipes()) {
            ItemStack output = recipe.getResultItem(registries);
            if (output.isEmpty()) {
                continue;
            }
            RecipeNode node = new RecipeNode(recipe.getId(), recipe, output, recipe.getIngredients());
            recipesByOutput.computeIfAbsent(IngredientKey.of(output), key -> new ArrayList<>()).add(node);
        }
        for (List<RecipeNode> nodes : recipesByOutput.values()) {
            nodes.sort(Comparator.comparingInt((RecipeNode node) -> node.inputs().size()).thenComparing(RecipeNode::idText));
        }
    }

    public static Optional<RecipeGraphCache> get(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Optional.empty();
        }
        if (current == null) {
            current = new RecipeGraphCache(minecraft);
        }
        return Optional.of(current);
    }

    public static void clear() {
        current = null;
    }

    public Optional<RecipeTree> createTree(ItemStack goal) {
        if (goal.isEmpty()) {
            return Optional.empty();
        }
        TreeBuildState state = new TreeBuildState();
        TreeNode root = buildNode(goal, goal.getCount(), 0, new HashSet<>(), state);
        return Optional.of(new RecipeTree(root));
    }

    public TreeNode createNode(ItemStack stack, int amount, int depth) {
        return buildNode(stack, amount, depth, new HashSet<>(), new TreeBuildState());
    }

    public List<RecipeNode> candidates(IngredientKey key) {
        List<RecipeNode> nodes = recipesByOutput.getOrDefault(key, List.of());
        int limit = Math.min(Config.maxCandidatesPerIngredient, nodes.size());
        return nodes.subList(0, limit);
    }

    private TreeNode buildNode(ItemStack stack, int amount, int depth, Set<IngredientKey> path, TreeBuildState state) {
        IngredientKey key = IngredientKey.of(stack);
        TreeNode node = new TreeNode(key, stack, amount, depth);
        if (!path.add(key)) {
            node.cycle(true);
            return node;
        }
        if (depth >= Config.maxTreeDepth || state.nodes >= Config.maxTreeNodes) {
            node.limited(true);
            path.remove(key);
            return node;
        }
        state.nodes++;

        List<RecipeNode> recipes = candidates(key);
        Optional<ResourceLocation> rememberedRecipe = RecipeSelectionMemory.selectedRecipe(stack);
        Optional<RecipeNode> rememberedNode = rememberedRecipe
                .flatMap(id -> recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst());
        if (rememberedNode.isEmpty() && recipes.size() != 1) {
            path.remove(key);
            return node;
        }

        RecipeNode recipe = rememberedNode.orElse(recipes.get(0));
        applyRecipe(node, recipe, amount, depth, path, state);
        path.remove(key);
        return node;
    }

    private void applyRecipe(TreeNode node, RecipeNode recipe, int amount, int depth, Set<IngredientKey> path, TreeBuildState state) {
        node.recipe(recipe);
        int outputCount = Math.max(1, recipe.output().getCount());
        int batches = (amount + outputCount - 1) / outputCount;
        node.baseBatches(batches);
        node.outputPerBatch(outputCount);
        node.batches(batches);
        Map<IngredientKey, ItemStack> displayStacks = new HashMap<>();
        Map<IngredientKey, Integer> counts = new HashMap<>();
        Map<IngredientKey, List<ItemStack>> alternativesByKey = new HashMap<>();
        for (Ingredient ingredient : recipe.inputs()) {
            ItemStack[] alternatives = ingredient.getItems();
            if (alternatives.length == 0) {
                continue;
            }
            ItemStack childStack = RecipeSelectionMemory.selectedAlternative(alternatives[0])
                    .filter(remembered -> ingredient.test(remembered))
                    .orElse(alternatives[0])
                    .copy();
            IngredientKey childKey = IngredientKey.of(childStack);
            displayStacks.putIfAbsent(childKey, childStack);
            counts.merge(childKey, childStack.getCount(), Integer::sum);
            alternativesByKey.putIfAbsent(childKey, List.of(alternatives));
        }
        for (Map.Entry<IngredientKey, Integer> entry : counts.entrySet()) {
            if (state.nodes >= Config.maxTreeNodes) {
                node.limited(true);
                break;
            }
            ItemStack childStack = displayStacks.get(entry.getKey()).copy();
            TreeNode child = buildNode(childStack, entry.getValue() * batches, depth + 1, path, state);
            child.alternatives(alternativesByKey.getOrDefault(entry.getKey(), List.of()));
            node.children().add(child);
        }
    }

    private static final class TreeBuildState {
        private int nodes;
    }
}
