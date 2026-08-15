package com.lhy.jeict.util;

import java.lang.reflect.Method;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

/**
 * 配方树本体不依赖 AE2，这里只保留与渲染/用量演算相关、无 AE2 依赖的工具方法。
 */
public final class GenericIngredientUtil {
    private static final ClassValue<Optional<Method>> CHEMICAL_COPY_WITH_AMOUNT = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            if (!"mekanism.api.chemical.ChemicalStack".equals(type.getName())) return Optional.empty();
            try {
                return Optional.of(type.getMethod("copyWithAmount", long.class));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return Optional.empty();
            }
        }
    };
    private GenericIngredientUtil() {
    }

    /** Returns a full-capacity copy for icon rendering without introducing a hard Mekanism dependency. */
    public static @Nullable Object tryCopyMekanismChemicalForIcon(@Nullable Object ingredient) {
        if (ingredient == null) return null;
        Optional<Method> method = CHEMICAL_COPY_WITH_AMOUNT.get(ingredient.getClass());
        if (method.isEmpty()) return null;
        try {
            return method.get().invoke(ingredient, Long.MAX_VALUE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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
