package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Registry for optional terminal-specific crafting result protocols. */
public final class CraftingResultInteractions {
    private static final List<CraftingResultInteraction> INTERACTIONS = new ArrayList<>();

    private CraftingResultInteractions() {
    }

    public static void register(CraftingResultInteraction interaction) {
        INTERACTIONS.add(interaction);
    }

    public static @Nullable Resolved find(AbstractContainerMenu menu, Player player, ItemStack expectedOutput) {
        for (CraftingResultInteraction interaction : INTERACTIONS) {
            CraftingResultInteraction.Match match = interaction.find(menu, player, expectedOutput);
            if (match != null) return new Resolved(interaction, match);
        }
        return null;
    }

    public record Resolved(CraftingResultInteraction interaction, CraftingResultInteraction.Match match) {
        public void take(Player player) {
            interaction.take(player, match);
        }

        public boolean executeBatch(Player player, long requestId, ItemStack expectedOutput) {
            return interaction.executeBatch(player, match, requestId, expectedOutput);
        }

        public void batchCompleted() {
            interaction.batchCompleted();
        }
    }
}