package com.lhy.jeict.compat.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.CraftingTermSlot;

import com.lhy.jeict.network.BatchCraftRequestPayload;
import com.lhy.jeict.network.BatchCraftResultPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-side AE2 batch executor with strict menu, slot, output and work-budget validation. */
public final class Ae2BatchCraftBootstrap {
    private static final int MAX_STACK_BATCHES = 4_096;
    private static final long MAX_BATCH_NANOS = 8_000_000L;

    private Ae2BatchCraftBootstrap() {
    }

    public static BatchCraftResultPayload execute(ServerPlayer player, BatchCraftRequestPayload request) {
        if (!(player.containerMenu instanceof CraftingTermMenu menu)
                || menu.containerId != request.containerId()
                || request.resultSlot() < 0
                || request.resultSlot() >= menu.slots.size()
                || !(menu.getSlot(request.resultSlot()) instanceof CraftingTermSlot resultSlot)
                || request.expectedOutput().isEmpty()
                || !ItemStack.isSameItemSameComponents(resultSlot.getItem(), request.expectedOutput())
                || !menu.getCarried().isEmpty()) {
            return BatchCraftResultPayload.rejected(request.requestId(), request.containerId());
        }

        int crafted = 0;
        int stored = 0;
        int playerRemainder = 0;
        long deadline = System.nanoTime() + MAX_BATCH_NANOS;
        for (int batch = 0; batch < MAX_STACK_BATCHES && System.nanoTime() < deadline; batch++) {
            ItemStack currentOutput = resultSlot.getItem();
            if (currentOutput.isEmpty()
                    || !ItemStack.isSameItemSameComponents(currentOutput, request.expectedOutput())) break;

            resultSlot.doClick(InventoryAction.CRAFT_STACK, player);
            ItemStack produced = menu.getCarried();
            if (produced.isEmpty()
                    || !ItemStack.isSameItemSameComponents(produced, request.expectedOutput())) break;

            int producedAmount = produced.getCount();
            crafted += producedAmount;
            AEItemKey key = AEItemKey.of(produced);
            long inserted = 0L;
            if (key != null && menu.getLinkStatus().connected()) {
                inserted = StorageHelper.poweredInsert(menu.getEnergySource(), menu.getHost().getInventory(), key,
                        producedAmount, menu.getActionSource());
            }
            int insertedAmount = (int) Math.min(producedAmount, Math.max(0L, inserted));
            stored += insertedAmount;
            produced.shrink(insertedAmount);
            if (!produced.isEmpty()) {
                playerRemainder += produced.getCount();
                ItemStack remainder = produced.copy();
                menu.setCarried(ItemStack.EMPTY);
                player.getInventory().add(remainder);
                if (!remainder.isEmpty()) menu.setCarried(remainder);
                break;
            }
            menu.setCarried(ItemStack.EMPTY);
        }
        return new BatchCraftResultPayload(request.requestId(), request.containerId(), crafted, stored,
                playerRemainder, true);
    }
}