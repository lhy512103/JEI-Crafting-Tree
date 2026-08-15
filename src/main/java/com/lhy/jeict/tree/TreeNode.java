package com.lhy.jeict.tree;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class TreeNode {
    private IngredientKey key;
    private ItemStack displayStack;
    private final int amount;
    private final int depth;
    private RecipeNode recipe;
    private final List<TreeNode> children = new ArrayList<>();
    private List<ItemStack> alternatives = List.of();
    private boolean expanded = true;
    private boolean cycle;
    private boolean limited;
    private boolean noRecipe;
    private int batches = 1;
    private int baseBatches = 1;
    private int outputPerBatch = 1;
    private int producedAmount;
    private int leftoverAmount;

    public TreeNode(IngredientKey key, ItemStack displayStack, int amount, int depth) {
        this.key = key;
        this.displayStack = displayStack.copy();
        this.amount = amount;
        this.depth = depth;
        this.producedAmount = amount;
    }

    public IngredientKey key() {
        return key;
    }

    public ItemStack displayStack() {
        ItemStack stack = displayStack.copy();
        stack.setCount(Math.max(1, amount));
        return stack;
    }

    public ItemStack singleStack() {
        ItemStack stack = displayStack.copy();
        stack.setCount(1);
        return stack;
    }

    public void replaceStack(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        this.key = IngredientKey.of(copy);
        this.displayStack = copy;
    }

    public List<ItemStack> alternatives() {
        return alternatives;
    }

    public void alternatives(List<ItemStack> alternatives) {
        this.alternatives = alternatives.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    return copy;
                })
                .toList();
    }

    public int amount() {
        return amount;
    }

    public int depth() {
        return depth;
    }

    public RecipeNode recipe() {
        return recipe;
    }

    public void recipe(RecipeNode recipe) {
        this.recipe = recipe;
    }

    public List<TreeNode> children() {
        return children;
    }

    public boolean expanded() {
        return expanded;
    }

    public void expanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public boolean cycle() {
        return cycle;
    }

    public void cycle(boolean cycle) {
        this.cycle = cycle;
    }

    public boolean limited() {
        return limited;
    }

    public void limited(boolean limited) {
        this.limited = limited;
    }

    public boolean noRecipe() {
        return noRecipe;
    }

    public void noRecipe(boolean noRecipe) {
        this.noRecipe = noRecipe;
    }

    public int batches() {
        return batches;
    }

    public int baseBatches() {
        return baseBatches;
    }

    public void baseBatches(int baseBatches) {
        this.baseBatches = Math.max(1, baseBatches);
    }

    public int outputPerBatch() {
        return outputPerBatch;
    }

    public void outputPerBatch(int outputPerBatch) {
        this.outputPerBatch = Math.max(1, outputPerBatch);
    }

    public void batches(int batches) {
        this.batches = Math.max(1, batches);
        this.producedAmount = this.batches * outputPerBatch;
        this.leftoverAmount = Math.max(0, producedAmount - amount);
    }

    public int producedAmount() {
        return producedAmount;
    }

    public void producedAmount(int producedAmount) {
        this.producedAmount = producedAmount;
    }

    public int leftoverAmount() {
        return leftoverAmount;
    }

    public void leftoverAmount(int leftoverAmount) {
        this.leftoverAmount = leftoverAmount;
    }
}
