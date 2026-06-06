package com.lhy.jeict.util;

import org.jetbrains.annotations.Nullable;

/**
 * 配方树本体不依赖 AE2，这里只保留与渲染/用量演算相关、无 AE2 依赖的工具方法。
 */
public final class GenericIngredientUtil {
    private GenericIngredientUtil() {
    }

    public static long tryGetMekanismChemicalAmount(@Nullable Object ingredient) {
        if (ingredient == null || !"mekanism.api.chemical.ChemicalStack".equals(ingredient.getClass().getName())) {
            return 0L;
        }
        try {
            Object rawAmount = ingredient.getClass().getMethod("getAmount").invoke(ingredient);
            if (rawAmount instanceof Number number) {
                return Math.max(0L, number.longValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return 0L;
    }
}
