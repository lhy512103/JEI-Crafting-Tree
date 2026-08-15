package com.lhy.jeict.tree;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires a bootstrapped Minecraft/Forge runtime; plain JUnit cannot initialize Forge NetworkEvent")
class TreeNodeTest {

    private static final Item OAK_PLANKS = new Item(new Item.Properties());
    private static final Item BIRCH_PLANKS = new Item(new Item.Properties());

    private static TreeNode node() {
        return new TreeNode(
                IngredientKey.of(new ItemStack(OAK_PLANKS)),
                new ItemStack(OAK_PLANKS),
                10,
                0
        );
    }

    @Test
    void initialBatchesIsOne() {
        TreeNode node = node();
        assertEquals(1, node.batches());
    }

    @Test
    void baseBatchesIsOne() {
        TreeNode node = node();
        assertEquals(1, node.baseBatches());
    }

    @Test
    void setBatchesUpdatesProducedAndLeftover() {
        TreeNode node = node();
        node.outputPerBatch(4);
        node.baseBatches(3);
        node.batches(5);
        assertEquals(5, node.batches());
        assertEquals(20, node.producedAmount());
        assertEquals(10, node.leftoverAmount());
    }

    @Test
    void batchesMinimumOfOne() {
        TreeNode node = node();
        node.batches(0);
        assertEquals(1, node.batches());
    }

    @Test
    void cycleStateTransitions() {
        TreeNode node = node();
        assertFalse(node.cycle());
        node.cycle(true);
        assertTrue(node.cycle());
    }

    @Test
    void limitedStateTransitions() {
        TreeNode node = node();
        assertFalse(node.limited());
        node.limited(true);
        assertTrue(node.limited());
    }

    @Test
    void noRecipeStateTransitions() {
        TreeNode node = node();
        assertFalse(node.noRecipe());
        node.noRecipe(true);
        assertTrue(node.noRecipe());
    }

    @Test
    void expandedDefaultsToTrue() {
        TreeNode node = node();
        assertTrue(node.expanded());
    }

    @Test
    void toggleExpandedFlipsState() {
        TreeNode node = node();
        node.toggleExpanded();
        assertFalse(node.expanded());
        node.toggleExpanded();
        assertTrue(node.expanded());
    }

    @Test
    void displayStackRespectsAmount() {
        TreeNode node = new TreeNode(
                IngredientKey.of(new ItemStack(OAK_PLANKS)),
                new ItemStack(OAK_PLANKS),
                5,
                0
        );
        ItemStack display = node.displayStack();
        assertEquals(5, display.getCount());
        assertEquals(OAK_PLANKS, display.getItem());
    }

    @Test
    void singleStackCountIsOne() {
        TreeNode node = node();
        ItemStack single = node.singleStack();
        assertEquals(1, single.getCount());
    }

    @Test
    void replaceStackUpdatesKeyAndDisplay() {
        TreeNode node = node();
        node.replaceStack(new ItemStack(BIRCH_PLANKS));
        assertEquals(IngredientKey.of(new ItemStack(BIRCH_PLANKS)), node.key());
        assertEquals(10, node.displayStack().getCount());
    }
}
