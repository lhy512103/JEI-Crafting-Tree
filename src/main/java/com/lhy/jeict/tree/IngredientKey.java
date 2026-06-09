package com.lhy.jeict.tree;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class IngredientKey {
    private final Item item;
    private final CompoundTag tag;

    private IngredientKey(Item item, CompoundTag tag) {
        this.item = item;
        this.tag = tag == null ? null : tag.copy();
    }

    public static IngredientKey of(ItemStack stack) {
        return new IngredientKey(stack.getItem(), stack.getTag());
    }

    public ItemStack stack(int count) {
        ItemStack stack = new ItemStack(item, count);
        if (tag != null) {
            stack.setTag(tag.copy());
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        return stack.is(item) && Objects.equals(tag, stack.getTag());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof IngredientKey other)) {
            return false;
        }
        return item == other.item && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, tag);
    }
}
