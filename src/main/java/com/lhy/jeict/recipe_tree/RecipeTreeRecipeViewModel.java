package com.lhy.jeict.recipe_tree;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class RecipeTreeRecipeViewModel {
    private final @Nullable ITypedIngredient<?> primaryOutputIngredient;
    private final ItemStack primaryOutput;
    private final long primaryOutputAmount;
    private final List<RecipeTreeOutputViewModel> outputs;
    private final Component title;
    private final @Nullable Component subtitle;
    private final @Nullable IDrawable subtitleIcon;
    private final @Nullable ResourceLocation recipeId;
    private final List<RecipeTreeInputViewModel> inputs;

    public RecipeTreeRecipeViewModel(@Nullable ITypedIngredient<?> primaryOutputIngredient, ItemStack primaryOutput,
            int primaryOutputAmount, Component title, @Nullable Component subtitle, @Nullable IDrawable subtitleIcon,
            @Nullable ResourceLocation recipeId, List<RecipeTreeInputViewModel> inputs) {
        this(primaryOutputIngredient, primaryOutput, primaryOutputAmount,
                List.of(new RecipeTreeOutputViewModel(primaryOutputIngredient, primaryOutput, primaryOutputAmount, 1.0D, true)),
                title, subtitle, subtitleIcon, recipeId, inputs);
    }

    public RecipeTreeRecipeViewModel(@Nullable ITypedIngredient<?> primaryOutputIngredient, ItemStack primaryOutput,
            long primaryOutputAmount, List<RecipeTreeOutputViewModel> outputs, Component title,
            @Nullable Component subtitle, @Nullable IDrawable subtitleIcon, @Nullable ResourceLocation recipeId,
            List<RecipeTreeInputViewModel> inputs) {
        this.primaryOutputIngredient = primaryOutputIngredient;
        this.primaryOutput = primaryOutput == null ? ItemStack.EMPTY : primaryOutput.copy();
        this.primaryOutputAmount = Math.max(1L, primaryOutputAmount);
        List<RecipeTreeOutputViewModel> copiedOutputs = new ArrayList<>(outputs == null ? List.of() : outputs);
        if (copiedOutputs.isEmpty()) {
            copiedOutputs.add(new RecipeTreeOutputViewModel(primaryOutputIngredient, this.primaryOutput,
                    this.primaryOutputAmount, 1.0D, true));
        }
        this.outputs = List.copyOf(copiedOutputs);
        this.title = title.copy();
        this.subtitle = subtitle == null ? null : subtitle.copy();
        this.subtitleIcon = subtitleIcon;
        this.recipeId = recipeId;
        this.inputs = List.copyOf(new ArrayList<>(inputs));
    }

    public @Nullable ITypedIngredient<?> primaryOutputIngredient() {
        return primaryOutputIngredient;
    }

    public ItemStack primaryOutput() {
        return primaryOutput.copy();
    }

    public Component title() {
        return title.copy();
    }

    public @Nullable Component subtitle() {
        return subtitle == null ? null : subtitle.copy();
    }

    public @Nullable IDrawable subtitleIcon() {
        return subtitleIcon;
    }

    public @Nullable ResourceLocation recipeId() {
        return recipeId;
    }

    public List<RecipeTreeInputViewModel> inputs() {
        return inputs;
    }

    public List<RecipeTreeOutputViewModel> outputs() {
        return outputs;
    }

    public List<RecipeTreeOutputViewModel> secondaryOutputs() {
        return outputs.stream().filter(output -> !output.primary()).toList();
    }

    public int primaryOutputCount() {
        return (int) Math.min(Integer.MAX_VALUE, primaryOutputAmount);
    }

    public long primaryOutputAmount() {
        return primaryOutputAmount;
    }

    public String stableIdentity() {
        if (recipeId != null) {
            return "id#" + recipeId;
        }
        return "view#" + title.getString() + "#" + primaryOutputAmount + "#"
                + (31 * primaryOutput.getItem().hashCode()
                + (primaryOutput.getTag() == null ? 0 : primaryOutput.getTag().hashCode()));
    }

    public boolean sameRecipeAs(RecipeTreeRecipeViewModel other) {
        if (recipeId != null && other.recipeId != null) {
            return recipeId.equals(other.recipeId);
        }
        return ItemStack.isSameItemSameTags(primaryOutput, other.primaryOutput)
                && title.getString().equals(other.title.getString());
    }
}
