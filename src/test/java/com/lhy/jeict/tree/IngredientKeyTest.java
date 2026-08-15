package com.lhy.jeict.tree;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires a bootstrapped Minecraft/Forge runtime; plain JUnit cannot initialize Forge NetworkEvent")
class IngredientKeyTest {

    private static final Item DIAMOND = new Item(new Item.Properties());
    private static final Item OAK_PLANKS = new Item(new Item.Properties());
    private static final Item BIRCH_PLANKS = new Item(new Item.Properties());
    private static final Item DIAMOND_SWORD = new Item(new Item.Properties());
    private static final Item IRON_INGOT = new Item(new Item.Properties());
    private static final Item GOLD_INGOT = new Item(new Item.Properties());
    private static final Item STONE = new Item(new Item.Properties());

    @Test
    void ofCreatesKeyFromItemStack() {
        ItemStack stack = new ItemStack(DIAMOND);
        IngredientKey key = IngredientKey.of(stack);
        assertNotNull(key);
        assertEquals(DIAMOND, key.stack(1).getItem());
    }

    @Test
    void equalsByItemAndTag() {
        IngredientKey key1 = IngredientKey.of(new ItemStack(OAK_PLANKS));
        IngredientKey key2 = IngredientKey.of(new ItemStack(OAK_PLANKS));
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void notEqualsForDifferentItems() {
        IngredientKey oak = IngredientKey.of(new ItemStack(OAK_PLANKS));
        IngredientKey birch = IngredientKey.of(new ItemStack(BIRCH_PLANKS));
        assertNotEquals(oak, birch);
    }

    @Test
    void notEqualsForDifferentTags() {
        ItemStack stack1 = new ItemStack(DIAMOND_SWORD);
        ItemStack stack2 = new ItemStack(DIAMOND_SWORD);
        CompoundTag tag1 = new CompoundTag();
        tag1.putString("name", "sword1");
        stack1.setTag(tag1);
        CompoundTag tag2 = new CompoundTag();
        tag2.putString("name", "sword2");
        stack2.setTag(tag2);
        assertNotEquals(IngredientKey.of(stack1), IngredientKey.of(stack2));
    }

    @Test
    void matchesReturnsTrueForMatchingStack() {
        ItemStack stack = new ItemStack(IRON_INGOT);
        IngredientKey key = IngredientKey.of(stack);
        assertTrue(key.matches(new ItemStack(IRON_INGOT)));
    }

    @Test
    void matchesReturnsFalseForDifferentItem() {
        ItemStack stack = new ItemStack(IRON_INGOT);
        IngredientKey key = IngredientKey.of(stack);
        assertFalse(key.matches(new ItemStack(GOLD_INGOT)));
    }

    @Test
    void stackReturnsCopyWithCount() {
        ItemStack stack = new ItemStack(STONE, 3);
        IngredientKey key = IngredientKey.of(stack);
        ItemStack result = key.stack(5);
        assertEquals(5, result.getCount());
        assertEquals(STONE, result.getItem());
    }
}
