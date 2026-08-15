package com.lhy.jeict.tree;

import com.lhy.jeict.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

/**
 * 配方图缓存。
 *
 * <p>历史实现是一个全局 static 单例，仅在 JEI 运行时关闭时清除；
 * 这意味着玩家切换世界 / 配方表 reload 后，缓存仍保留旧 {@link RegistryAccess}
 * 与旧 {@link Recipe} 引用，会出现查询错位。本实现改为：
 * <ul>
 *   <li>按 {@link ClientLevel} 维度键分桶，离开世界即自动失效；</li>
 *   <li>对外暴露 {@link #clear()} 在 reload / unload 时主动调用；</li>
 *   <li>{@link #get(Minecraft)} 加主线程检查，避免异步路径触发构建。</li>
 * </ul>
 */
public final class RecipeGraphCache {
    private static final Map<ResourceLocation, RecipeGraphCache> BY_LEVEL = new HashMap<>();

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
        if (minecraft == null || minecraft.level == null) {
            return Optional.empty();
        }
        // 防御：避免异步路径触发构建（recipe manager 读取必须在主线程）
        if (!minecraft.isSameThread()) {
            return Optional.empty();
        }
        ResourceLocation levelKey = minecraft.level.dimension().location();
        return Optional.of(BY_LEVEL.computeIfAbsent(levelKey, key -> new RecipeGraphCache(minecraft)));
    }

    public static void clear() {
        BY_LEVEL.clear();
    }

    /** 仅失效指定维度的缓存（用于单世界 unload）。 */
    public static void clear(ResourceLocation levelKey) {
        if (levelKey == null) {
            return;
        }
        BY_LEVEL.remove(levelKey);
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
        int limit = Math.min(Config.maxCandidatesPerIngredient(), nodes.size());
        return nodes.subList(0, limit);
    }

    private TreeNode buildNode(ItemStack stack, int amount, int depth, Set<IngredientKey> path, TreeBuildState state) {
        IngredientKey key = IngredientKey.of(stack);
        TreeNode node = new TreeNode(key, stack, amount, depth);
        if (!path.add(key)) {
            node.cycle(true);
            return node;
        }
        if (depth >= Config.maxTreeDepth() || state.nodes >= Config.maxTreeNodes()) {
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
            // 没有可选配方（候选数为 0 或大于 1 且没记忆）。标记为 noRecipe 让 UI 显式提示。
            node.noRecipe(true);
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
            // 路径 4：统一把展示用 stack 拉平成 count=1，原 count 通过独立通道累加，避免 NBT 带 count 的极端情况。
            int originalCount = Math.max(1, childStack.getCount());
            childStack.setCount(1);
            IngredientKey childKey = IngredientKey.of(childStack);
            displayStacks.putIfAbsent(childKey, childStack);
            counts.merge(childKey, originalCount, Integer::sum);
            alternativesByKey.putIfAbsent(childKey, List.of(alternatives));
        }
        for (Map.Entry<IngredientKey, Integer> entry : counts.entrySet()) {
            if (state.nodes >= Config.maxTreeNodes()) {
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
