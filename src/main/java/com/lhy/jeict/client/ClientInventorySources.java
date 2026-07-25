package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lhy.jeict.api.CraftingTreeInventorySources;
import com.lhy.jeict.api.InventoryAmount;
import com.lhy.jeict.api.InventorySource;
import com.lhy.jeict.api.MenuInventorySource;
import com.lhy.jeict.compat.ae2.Ae2CompatBootstrap;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;

import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;

/** Registers player stock and one authoritative provider for the currently open menu. */
public final class ClientInventorySources {
    private static boolean registered;

    private ClientInventorySources() {
    }

    public static void registerBuiltIns() {
        if (registered) return;
        registered = true;
        ClientMenuInventoryProviders.register(new SlotMenuInventoryProvider());
        Ae2CompatBootstrap.registerIfLoaded();
        CraftingTreeInventorySources.register(new ClientInventorySource());
    }

    private static final class ClientInventorySource implements InventorySource {
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
            var runtime = JeiCraftingTreePlugin.getJeiRuntime();
            if (minecraft.player == null || runtime == null) return 0L;
            long hash = minecraft.player.getInventory().getTimesChanged();
            var menu = minecraft.player.containerMenu;
            MenuInventorySource provider = ClientMenuInventoryProviders.select(menu);
            if (menu == null || provider == null) return hash;
            hash = 31L * hash + menu.containerId;
            hash = 31L * hash + provider.id().hashCode();
            return 31L * hash + provider.version(menu, runtime.getIngredientManager());
        }

        @Override
        public List<InventoryAmount> snapshot() {
            Minecraft minecraft = Minecraft.getInstance();
            var runtime = JeiCraftingTreePlugin.getJeiRuntime();
            if (minecraft.player == null || runtime == null) return List.of();
            IIngredientManager manager = runtime.getIngredientManager();
            Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
            minecraft.player.getInventory().items.forEach(stack -> SlotMenuInventoryProvider.add(manager, amounts, stack));
            minecraft.player.getInventory().offhand.forEach(stack -> SlotMenuInventoryProvider.add(manager, amounts, stack));
            minecraft.player.getInventory().armor.forEach(stack -> SlotMenuInventoryProvider.add(manager, amounts, stack));

            var menu = minecraft.player.containerMenu;
            MenuInventorySource provider = ClientMenuInventoryProviders.select(menu);
            if (menu != null && provider != null) {
                for (InventoryAmount entry : provider.snapshot(menu, manager)) {
                    amounts.merge(entry.material(), entry.amount(), RecipePlanSolver::saturatedAdd);
                }
            }
            List<InventoryAmount> result = new ArrayList<>(amounts.size());
            amounts.forEach((key, amount) -> result.add(new InventoryAmount(key, amount)));
            return List.copyOf(result);
        }
    }
}