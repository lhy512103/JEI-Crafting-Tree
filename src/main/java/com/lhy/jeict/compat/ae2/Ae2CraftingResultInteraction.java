package com.lhy.jeict.compat.ae2;

import org.jetbrains.annotations.Nullable;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;

import com.lhy.jeict.client.CraftingResultInteraction;
import com.lhy.jeict.network.BatchCraftRequestPayload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Uses AE2's crafting action packet because CraftingTermSlot intentionally rejects vanilla pickup. */
public final class Ae2CraftingResultInteraction implements CraftingResultInteraction {
    @Override
    public @Nullable Match find(AbstractContainerMenu menu, Player player, ItemStack expectedOutput) {
        if (expectedOutput.isEmpty()) return null;
        for (Slot slot : menu.slots) {
            if (slot instanceof CraftingTermSlot
                    && ItemStack.isSameItemSameComponents(slot.getItem(), expectedOutput)) {
                return new Match(slot, true);
            }
        }
        return null;
    }

    @Override
    public boolean executeBatch(Player player, Match match, long requestId, ItemStack expectedOutput) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BatchCraftRequestPayload.TYPE)) return false;
        PacketDistributor.sendToServer(new BatchCraftRequestPayload(requestId, player.containerMenu.containerId,
                match.slot().index, expectedOutput.copyWithCount(1)));
        return true;
    }

    @Override
    public void batchCompleted() {
        Ae2ClientMenuInventoryProvider.invalidateSnapshot();
    }

    @Override
    public void take(Player player, Match match) {
        PacketDistributor.sendToServer(
                new InventoryActionPacket(InventoryAction.CRAFT_SHIFT, match.slot().index, 0));
    }
}