package com.lhy.jeict.util;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.planning.MaterialKey;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

/** Central component/subtype-aware JEI identity helper used by lookup, memory, planning, and caches. */
public final class IngredientIdentityUtil {
    private IngredientIdentityUtil() {
    }

    public static MaterialKey keyOf(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return keyOfTyped(ingredientManager, ingredient);
    }

    public static String signatureOf(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return keyOf(ingredientManager, ingredient).encoded();
    }

    public static String fallbackSignature(@Nullable ITypedIngredient<?> ingredient, @Nullable ItemStack itemStack) {
        if (ingredient != null) {
            Object raw = ingredient.getIngredient();
            return ingredient.getType().getUid() + "#fallback:" + raw.getClass().getName() + ":" + raw;
        }
        if (itemStack != null && !itemStack.isEmpty()) {
            return "minecraft:item_stack#" + ItemStack.hashItemAndComponents(itemStack) + ":" + itemStack.getItem();
        }
        return "unknown#empty";
    }

    private static <T> MaterialKey keyOfTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(typed.getType());
        return new MaterialKey(typed.getType().getUid().toString(), String.valueOf(helper.getUid(typed, UidContext.Ingredient)));
    }
}
