package com.lhy.jeict.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Handles result slots that use a mod-specific click protocol instead of vanilla container clicks. */
public interface CraftingResultInteraction {
    @Nullable Match find(AbstractContainerMenu menu, Player player, ItemStack expectedOutput);

    void take(Player player, Match match);

    default boolean executeBatch(Player player, Match match, long requestId, ItemStack expectedOutput) {
        return false;
    }

    default void batchCompleted() {
    }

    record Match(Slot slot, boolean deliversToPlayerInventory) {
    }
}