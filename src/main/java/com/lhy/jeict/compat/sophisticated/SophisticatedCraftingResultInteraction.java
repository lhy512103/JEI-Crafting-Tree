package com.lhy.jeict.compat.sophisticated;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.client.CraftingResultInteraction;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SophisticatedCraftingResultInteraction implements CraftingResultInteraction {
    @Override
    public @Nullable Match find(AbstractContainerMenu menu, Player player, ItemStack expectedOutput) {
        Slot slot = SophisticatedCraftingClient.findResultSlot(menu, player, expectedOutput);
        return slot == null ? null : new Match(slot, false);
    }

    @Override
    public boolean executeBatch(Player player, Match match, long requestId, ItemStack expectedOutput) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(SophisticatedFastCraftRequestPayload.TYPE)) return false;
        PacketDistributor.sendToServer(new SophisticatedFastCraftRequestPayload(requestId,
                player.containerMenu.containerId, match.slot().index, expectedOutput.copyWithCount(1)));
        return true;
    }

    @Override
    public void take(Player player, Match match) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, match.slot().index, 0,
                    ClickType.QUICK_MOVE, player);
        }
    }
}
