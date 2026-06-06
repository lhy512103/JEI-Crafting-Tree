package com.lhy.jeict.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RecipeTreeTransferMenu extends AbstractContainerMenu {
    private @Nullable RecipeTreeJeiTransferTarget target;

    public RecipeTreeTransferMenu() {
        super(null, 0);
    }

    public void jeict$setTarget(RecipeTreeJeiTransferTarget target) {
        this.target = target;
    }

    public @Nullable RecipeTreeJeiTransferTarget jeict$getTarget() {
        return target;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public static Inventory jeict$getPlayerInventory() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("Recipe tree screen requires a client player");
        }
        return player.getInventory();
    }
}
