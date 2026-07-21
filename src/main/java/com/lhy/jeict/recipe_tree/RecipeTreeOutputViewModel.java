package com.lhy.jeict.recipe_tree;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

/**
 * One output slot of a JEI recipe snapshot.
 *
 * <p>The crafting tree keeps every visible output instead of discarding all but
 * the first one. The first/focused output remains the primary output for tree
 * expansion while the remaining outputs can participate in surplus planning.
 */
public record RecipeTreeOutputViewModel(
        @Nullable ITypedIngredient<?> ingredient,
        ItemStack itemStack,
        long amount,
        double chance,
        boolean primary) {

    public RecipeTreeOutputViewModel {
        itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        amount = Math.max(1L, amount);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    public RecipeTreeOutputViewModel withPrimary(boolean value) {
        return value == primary ? this : new RecipeTreeOutputViewModel(ingredient, itemStack, amount, chance, value);
    }

    public int amountAsInt() {
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }
}
