package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lhy.jeict.api.CraftingTreeInventorySources;
import com.lhy.jeict.api.InventoryAmount;
import com.lhy.jeict.api.InventorySource;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.util.IngredientIdentityUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Registers the player inventory and currently open non-player menu as a live planning source. */
public final class ClientInventorySources {
    private static boolean registered;

    private ClientInventorySources() {
    }

    public static void registerBuiltIns() {
        if (registered) return;
        registered = true;
        CraftingTreeInventorySources.register(new ClientMenuInventorySource());
    }

    private static final class ClientMenuInventorySource implements InventorySource {
        @Override
        public String id() {
            return "jeict:player_and_open_menu";
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public boolean isAvailable() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null && JeiCraftingTreePlugin.getJeiRuntime() != null;
        }

        @Override
        public long version() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return 0L;
            long playerVersion = minecraft.player.getInventory().getTimesChanged();
            var menu = minecraft.player.containerMenu;
            long menuVersion = menu == null ? 0L : menu.getStateId();
            long menuId = menu == null ? 0L : menu.containerId;
            return (playerVersion << 32) ^ menuVersion ^ menuId;
        }

        @Override
        public List<InventoryAmount> snapshot() {
            Minecraft minecraft = Minecraft.getInstance();
            var runtime = JeiCraftingTreePlugin.getJeiRuntime();
            if (minecraft.player == null || runtime == null) return List.of();
            IIngredientManager manager = runtime.getIngredientManager();
            Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
            for (ItemStack stack : minecraft.player.getInventory().items) {
                add(manager, amounts, stack);
            }
            for (ItemStack stack : minecraft.player.getInventory().offhand) {
                add(manager, amounts, stack);
            }
            for (ItemStack stack : minecraft.player.getInventory().armor) {
                add(manager, amounts, stack);
            }
            if (minecraft.player.containerMenu != null) {
                for (Slot slot : minecraft.player.containerMenu.slots) {
                    if (slot.container != minecraft.player.getInventory()) add(manager, amounts, slot.getItem());
                }
            }
            List<InventoryAmount> result = new ArrayList<>(amounts.size());
            amounts.forEach((key, amount) -> result.add(new InventoryAmount(key, amount)));
            return List.copyOf(result);
        }

        private static void add(IIngredientManager manager, Map<MaterialKey, Long> amounts, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;
            ITypedIngredient<?> typed = manager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
            if (typed == null) return;
            amounts.merge(IngredientIdentityUtil.keyOf(manager, typed), (long) stack.getCount(), RecipePlanSolver::saturatedAdd);
        }
    }
}
