package com.lhy.jeict.compat.ae2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.jeict.api.InventoryAmount;
import com.lhy.jeict.client.ClientMenuInventoryProvider;
import com.lhy.jeict.client.ClientMenuInventoryProviders;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.util.IngredientIdentityUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Versioned client-side view of the stock AE2 already synchronizes to an open ME terminal. */
public final class Ae2ClientMenuInventoryProvider implements ClientMenuInventoryProvider {
    private static final long REFRESH_INTERVAL_TICKS = 5L;
    private static final Ae2ClientMenuInventoryProvider INSTANCE = new Ae2ClientMenuInventoryProvider();

    private AbstractContainerMenu cachedMenu;
    private long nextRefreshTick;
    private long cachedVersion;
    private List<InventoryAmount> cachedSnapshot = List.of();

    private Ae2ClientMenuInventoryProvider() {
    }

    public static void register() {
        ClientMenuInventoryProviders.register(INSTANCE);
    }

    public static void invalidateSnapshot() {
        INSTANCE.cachedMenu = null;
        INSTANCE.nextRefreshTick = 0L;
    }

    @Override
    public String id() {
        return "jeict:ae2_terminal";
    }

    @Override
    public int priority() {
        return 1_000;
    }

    @Override
    public boolean supports(AbstractContainerMenu menu) {
        return menu instanceof MEStorageMenu;
    }

    @Override
    public long version(AbstractContainerMenu menu, IIngredientManager ingredientManager) {
        refresh((MEStorageMenu) menu, ingredientManager);
        return cachedVersion;
    }

    @Override
    public List<InventoryAmount> snapshot(AbstractContainerMenu menu, IIngredientManager ingredientManager) {
        refresh((MEStorageMenu) menu, ingredientManager);
        return cachedSnapshot;
    }

    private void refresh(MEStorageMenu menu, IIngredientManager ingredientManager) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (cachedMenu == menu && gameTime < nextRefreshTick) return;
        cachedMenu = menu;
        nextRefreshTick = gameTime + REFRESH_INTERVAL_TICKS;

        Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
        long fingerprint = menu.containerId;
        var repo = menu.getClientRepo();
        if (repo != null && menu.getLinkStatus().connected()) {
            for (GridInventoryEntry entry : repo.getAllEntries()) {
                AEKey what = entry.getWhat();
                long amount = Math.max(0L, entry.getStoredAmount());
                if (what == null || amount <= 0L) continue;
                MaterialKey material = materialKey(ingredientManager, what);
                if (material == null) continue;
                amounts.merge(material, amount, RecipePlanSolver::saturatedAdd);
                fingerprint = 31L * fingerprint + what.hashCode();
                fingerprint = 31L * fingerprint + Long.hashCode(amount);
            }
        }

        var player = minecraft.player;
        if (player != null) {
            for (Slot slot : menu.slots) {
                if (slot.container == player.getInventory()) continue;
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                ITypedIngredient<?> typed = ingredientManager
                        .createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
                if (typed == null) continue;
                MaterialKey material = IngredientIdentityUtil.keyOf(ingredientManager, typed);
                amounts.merge(material, (long) stack.getCount(), RecipePlanSolver::saturatedAdd);
                fingerprint = 31L * fingerprint + ItemStack.hashItemAndComponents(stack);
                fingerprint = 31L * fingerprint + stack.getCount();
            }
        }

        List<InventoryAmount> next = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> next.add(new InventoryAmount(key, amount)));
        cachedSnapshot = List.copyOf(next);
        cachedVersion = fingerprint;
    }

    private static MaterialKey materialKey(IIngredientManager manager, AEKey key) {
        Object ingredient = switch (key) {
            case AEItemKey itemKey -> itemKey.toStack();
            case AEFluidKey fluidKey -> fluidKey.toStack(1);
            default -> null;
        };
        if (ingredient == null) return null;
        ITypedIngredient<?> typed = manager.createTypedIngredient(ingredient, true).orElse(null);
        return typed == null ? null : IngredientIdentityUtil.keyOf(manager, typed);
    }
}