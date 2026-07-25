package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lhy.jeict.api.InventoryAmount;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.util.IngredientIdentityUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fallback provider for real item slots exposed by vanilla and modded menus. */
final class SlotMenuInventoryProvider implements ClientMenuInventoryProvider {
    @Override
    public String id() {
        return "jeict:menu_slots";
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean supports(AbstractContainerMenu menu) {
        return true;
    }

    @Override
    public long version(AbstractContainerMenu menu, IIngredientManager ingredientManager) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0L;
        long hash = menu.containerId;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            hash = 31L * hash + stackFingerprint(stack);
        }
        return hash;
    }

    @Override
    public List<InventoryAmount> snapshot(AbstractContainerMenu menu, IIngredientManager ingredientManager) {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) continue;
            add(ingredientManager, amounts, slot.getItem());
        }
        List<InventoryAmount> result = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> result.add(new InventoryAmount(key, amount)));
        return List.copyOf(result);
    }

    private static long stackFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        long hash = stack.getItem().hashCode();
        hash = 31L * hash + stack.getCount();
        return 31L * hash + stack.getComponents().hashCode();
    }

    static void add(IIngredientManager manager, Map<MaterialKey, Long> amounts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ITypedIngredient<?> typed = manager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
        if (typed == null) return;
        amounts.merge(IngredientIdentityUtil.keyOf(manager, typed), (long) stack.getCount(),
                RecipePlanSolver::saturatedAdd);
    }
}