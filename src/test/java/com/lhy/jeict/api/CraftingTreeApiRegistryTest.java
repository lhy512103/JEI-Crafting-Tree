package com.lhy.jeict.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lhy.jeict.planning.MaterialKey;

class CraftingTreeApiRegistryTest {
    @Test
    void backendPriorityAndRegistrationHandleAreDeterministic() {
        TestBackend low = new TestBackend();
        TestBackend high = new TestBackend();
        ApiRegistration lowHandle = CraftingTreeBackends.register("test:low", 1, low);
        ApiRegistration highHandle = CraftingTreeBackends.register("test:high", 10, high);
        try {
            assertSame(high, CraftingTreeBackends.get());
            highHandle.unregister();
            highHandle.unregister();
            assertSame(low, CraftingTreeBackends.get());
        } finally {
            lowHandle.unregister();
            CraftingTreeBackends.unregister("test:high");
        }
        assertFalse(CraftingTreeBackends.isPresent());
    }

    @Test
    void inventoryAuthorityGroupPreventsDuplicateStorageAggregation() {
        MaterialKey iron = new MaterialKey("item", "minecraft:iron_ingot");
        InventorySource fallback = source("test:fallback", "test:network", 1, iron, 4);
        InventorySource authoritative = source("test:authoritative", "test:network", 10, iron, 9);
        ApiRegistration fallbackHandle = CraftingTreeInventorySources.registerWithHandle(fallback);
        ApiRegistration authoritativeHandle = CraftingTreeInventorySources.registerWithHandle(authoritative);
        try {
            assertEquals(9L, CraftingTreeInventorySources.aggregate().amounts().get(iron));
        } finally {
            authoritativeHandle.unregister();
            fallbackHandle.unregister();
        }
    }

    private static InventorySource source(String id, String group, int priority, MaterialKey material, long amount) {
        return new InventorySource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String authorityGroup() {
                return group;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public List<InventoryAmount> snapshot() {
                return List.of(new InventoryAmount(material, amount));
            }
        };
    }

    private static final class TestBackend implements CraftingTreeBackend {
        @Override
        public boolean isCraftable(Object rawIngredient) {
            return false;
        }

        @Override
        public boolean encodePatterns(List<com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel> selectedRecipes) {
            return false;
        }

        @Override
        public boolean uploadPatterns(List<com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel> selectedRecipes) {
            return false;
        }
    }
}