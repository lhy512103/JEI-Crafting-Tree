package com.lhy.jeict.compat.ae2;

import org.jetbrains.annotations.Nullable;

import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;

import com.lhy.jeict.client.CraftingResultInteraction;
import com.lhy.jeict.network.BatchCraftRequestPayload;
import com.lhy.jeict.network.JeictNetworking;

import appeng.core.sync.network.NetworkHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Uses AE2's crafting action packet because CraftingTermSlot intentionally rejects vanilla pickup. */
public final class Ae2CraftingResultInteraction implements CraftingResultInteraction {
    @Override
    public @Nullable Match find(AbstractContainerMenu menu, Player player, ItemStack expectedOutput) {
        if (expectedOutput.isEmpty()) return null;
        for (Slot slot : menu.slots) {
            if (slot instanceof CraftingTermSlot
                    && ItemStack.isSameItemSameTags(slot.getItem(), expectedOutput)) {
                return new Match(slot, true);
            }
        }
        return null;
    }

    @Override
    public boolean executeBatch(Player player, Match match, long requestId, ItemStack expectedOutput) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return false;
        JeictNetworking.CHANNEL.sendToServer(new BatchCraftRequestPayload(requestId, player.containerMenu.containerId,
                match.slot().index, expectedOutput.copyWithCount(1)));
        return true;
    }

    @Override
    public void batchCompleted() {
        Ae2ClientMenuInventoryProvider.invalidateSnapshot();
    }

    @Override
    public void take(Player player, Match match) {
        NetworkHandler.instance().sendToServer(
                new InventoryActionPacket(InventoryAction.CRAFT_SHIFT, match.slot().index, 0L));
    }
}
