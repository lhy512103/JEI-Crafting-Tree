package com.lhy.jeict.recipe_tree;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * 一个配方输入槽里的一组可互换备选材料及其所需数量。
 * 独立于任何 AE2/网络层，便于配方树脱离 AE2 Utility 单独运行。
 */
public record RequestedIngredient(List<ItemStack> alternatives, int count) {
    public RequestedIngredient(List<ItemStack> alternatives, int count) {
        this.alternatives = alternatives.stream().map(ItemStack::copy).toList();
        this.count = count;
    }

    public RequestedIngredient copy() {
        return new RequestedIngredient(alternatives, count);
    }
}
