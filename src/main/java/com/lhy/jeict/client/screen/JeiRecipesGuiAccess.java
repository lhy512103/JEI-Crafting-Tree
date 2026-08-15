package com.lhy.jeict.client.screen;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 集中管理 JEI 15 内部反射访问。JEI 15 没有公开 API 获取当前 RecipesGui 的可见配方布局列表。
 */
public final class JeiRecipesGuiAccess {
    private static final String RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";

    private static @Nullable Field layoutsField;
    private static @Nullable Field recipeLayoutsWithButtonsField;
    private static @Nullable Method recipeLayoutMethod;
    private static boolean reflectionFailed;

    private JeiRecipesGuiAccess() {
    }

    public static boolean isRecipesGui(@Nullable Screen screen) {
        return screen != null && RECIPES_GUI_CLASS.equals(screen.getClass().getName());
    }

    public static List<IRecipeLayoutDrawable<?>> getVisibleRecipeLayouts(Screen screen) {
        List<?> rows = getVisibleRecipeLayoutRows(screen);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<IRecipeLayoutDrawable<?>> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            IRecipeLayoutDrawable<?> layout = getRecipeLayout(row);
            if (layout != null) {
                result.add(layout);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<?> getVisibleRecipeLayoutRows(Screen screen) {
        if (reflectionFailed || !isRecipesGui(screen)) {
            return null;
        }
        try {
            if (layoutsField == null) {
                layoutsField = screen.getClass().getDeclaredField("layouts");
                layoutsField.setAccessible(true);
            }
            Object layouts = layoutsField.get(screen);
            if (layouts == null) {
                return null;
            }
            if (recipeLayoutsWithButtonsField == null) {
                recipeLayoutsWithButtonsField = layouts.getClass().getDeclaredField("recipeLayoutsWithButtons");
                recipeLayoutsWithButtonsField.setAccessible(true);
            }
            Object value = recipeLayoutsWithButtonsField.get(layouts);
            return value instanceof List<?> list ? list : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    private static @Nullable IRecipeLayoutDrawable<?> getRecipeLayout(Object layoutWithButtons) {
        if (layoutWithButtons == null || reflectionFailed) {
            return null;
        }
        try {
            if (recipeLayoutMethod == null) {
                recipeLayoutMethod = layoutWithButtons.getClass().getDeclaredMethod("recipeLayout");
                recipeLayoutMethod.setAccessible(true);
            }
            Object value = recipeLayoutMethod.invoke(layoutWithButtons);
            return value instanceof IRecipeLayoutDrawable<?> drawable ? drawable : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }
}
