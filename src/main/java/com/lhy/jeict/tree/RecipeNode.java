package com.lhy.jeict.tree;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

public final class RecipeNode {
    private final ResourceLocation id;
    private final Recipe<?> recipe;
    private final ItemStack output;
    private final List<Ingredient> inputs;

    public RecipeNode(ResourceLocation id, Recipe<?> recipe, ItemStack output, List<Ingredient> inputs) {
        this.id = id;
        this.recipe = recipe;
        this.output = output.copy();
        this.inputs = List.copyOf(inputs);
    }

    public ResourceLocation id() {
        return id;
    }

    public Recipe<?> recipe() {
        return recipe;
    }

    public ItemStack output() {
        return output.copy();
    }

    public List<Ingredient> inputs() {
        return inputs;
    }

    public RecipeType<?> type() {
        return recipe.getType();
    }

    public String idText() {
        return id.toString();
    }
}
