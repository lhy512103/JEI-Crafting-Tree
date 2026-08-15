package com.lhy.jeict.compat.sophisticated;

import com.lhy.jeict.network.BatchCraftResultPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

public final class SophisticatedFastCraftServer {
    private static final int MAX_CRAFTS = 64;
    private static final long MAX_NANOS = 3_000_000L;

    private SophisticatedFastCraftServer() {}

    public static BatchCraftResultPayload execute(ServerPlayer player, SophisticatedFastCraftRequestPayload request) {
        if (player.containerMenu.containerId != request.containerId()
                || !(player.containerMenu instanceof StorageContainerMenuBase<?> menu)
                || request.resultSlot() < 0 || request.resultSlot() >= menu.getTotalSlotsNumber()) {
            return BatchCraftResultPayload.rejected(request.requestId(), request.containerId());
        }
        Slot resultSlot = menu.getSlot(request.resultSlot());
        long deadline = System.nanoTime() + MAX_NANOS;
        int crafted = 0;
        for (int attempt = 0; attempt < MAX_CRAFTS && System.nanoTime() < deadline; attempt++) {
            ItemStack visible = resultSlot.getItem();
            if (visible.isEmpty() || !ItemStack.isSameItemSameTags(visible, request.expectedOutput())) break;
            ItemStack moved = menu.quickMoveStack(player, request.resultSlot());
            if (moved.isEmpty()) break;
            crafted += moved.getCount();
        }
        menu.broadcastChanges();
        return new BatchCraftResultPayload(request.requestId(), request.containerId(), crafted, crafted, 0,
                crafted > 0);
    }
}
