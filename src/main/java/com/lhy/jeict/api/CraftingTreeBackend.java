package com.lhy.jeict.api;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 由外部模组（如 AE2 Utility）实现并通过 {@link CraftingTreeBackends#register} 注册，
 * 为配方树界面提供 AE2 相关能力：ME「已有样板」检测、一键编码/上传、AE2 替换开关 UI。
 *
 * <p>JEI Crafting Tree 本体不依赖 AE2。未注册任何后端时，配方树退化为纯规划树
 * （递归展开 + 用量演算 + 选配方），所有 AE2 相关按钮与高亮自动隐藏。
 */
public interface CraftingTreeBackend {

    /** 是否支持「ME 已有样板」检测（红框/隐藏已有分支等）。 */
    default boolean supportsExistingPatternHints() {
        return true;
    }

    /** 是否支持「编码样板」按钮。 */
    default boolean supportsEncode() {
        return true;
    }

    /** 是否支持「上传到供应器」按钮（通常需要 ExtendedAE Plus）。 */
    default boolean supportsUpload() {
        return false;
    }

    /** 是否支持 AE2 替换（substitution）开关。 */
    default boolean supportsSubstitution() {
        return true;
    }

    /**
     * 指定的 JEI 原料（{@code ITypedIngredient#getIngredient()} 的原始对象，
     * 例如 ItemStack / FluidStack / Mekanism ChemicalStack）在 ME 网络中是否已有样板/可合成。
     */
    boolean isCraftable(@Nullable Object rawIngredient);

    /** 每 tick 调用，返回 true 表示「已有样板」相关缓存发生变化，界面需刷新。 */
    default boolean pollExistingPatternCachesStale() {
        return false;
    }

    /** 给定配方的输出是否更适合用「严格可编码」判据（用于自动展开唯一配方）。 */
    default boolean isStrictEncodable(RecipeTreeRecipeViewModel recipe) {
        return recipe.primaryOutputIngredient() != null;
    }

    /** 一键编码选中的配方（非上传模式）。返回 true 表示编码已发出、界面应关闭。 */
    boolean encodePatterns(List<RecipeTreeRecipeViewModel> selectedRecipes);

    /** 一键编码并上传到供应器（上传模式）。返回 true 表示已开始上传、界面应关闭。 */
    boolean uploadPatterns(List<RecipeTreeRecipeViewModel> selectedRecipes);

    // ---- AE2 替换开关 UI（图标取自 AE2，故由后端渲染） ----

    default boolean itemSubstituteOn() {
        return false;
    }

    default boolean fluidSubstituteOn() {
        return false;
    }

    default void toggleItemSubstitute() {
    }

    default void toggleFluidSubstitute() {
    }

    /** 在 (x,y) 处渲染替换开关图标，srcSize 为图标源尺寸，dstSize 为目标绘制尺寸。 */
    default void renderSubstitutionIcon(GuiGraphics graphics, int x, int y, int srcSize, int dstSize, boolean fluid) {
    }

    /** 替换开关的悬停提示文本。 */
    default List<Component> substitutionTooltip(boolean fluid) {
        return List.of();
    }
}
