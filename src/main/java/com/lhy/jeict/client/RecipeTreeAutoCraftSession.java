package com.lhy.jeict.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only, one-operation-at-a-time execution loop for a currently open crafting menu.
 * JEI remains responsible for material transfer; the normal menu click protocol performs crafting.
 */
public final class RecipeTreeAutoCraftSession {
    private static final int MAX_OPERATIONS = 128;
    private static final int SYNC_TIMEOUT_TICKS = 20;

    private static @Nullable Session session;

    private RecipeTreeAutoCraftSession() {
    }

    public enum StopReason {
        COMPLETED,
        CANCELLED,
        MENU_CHANGED,
        NO_OUTPUT,
        NO_SPACE,
        TRANSFER_FAILED,
        SYNC_TIMEOUT,
        OPERATION_LIMIT
    }

    public record Status(boolean running, @Nullable StopReason stopReason, @Nullable Component recipeTitle) {
    }

    public static boolean toggle(@Nullable RecipeTreeRootContext context) {
        if (session != null) {
            stop(StopReason.CANCELLED);
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (context == null || player == null || minecraft.gameMode == null) {
            return false;
        }
        session = new Session(context, player.containerMenu.containerId);
        return true;
    }

    public static void tick() {
        Session current = session;
        if (current == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.gameMode == null || player.containerMenu.containerId != current.containerId) {
            stop(StopReason.MENU_CHANGED);
            return;
        }
        if (++current.waitTicks > SYNC_TIMEOUT_TICKS) {
            stop(StopReason.SYNC_TIMEOUT);
            return;
        }

        switch (current.phase) {
            case FILL -> fill(current, player);
            case WAIT_OUTPUT -> awaitOutput(current, player);
            case TAKE_OUTPUT -> takeOutput(current, player);
            case STORE_CURSOR -> storeCursor(current, player);
            case WAIT_STORE -> awaitStored(current, player);
        }
    }

    public static Status status() {
        Session current = session;
        return current == null ? new Status(false, lastStopReason, lastRecipeTitle)
                : new Status(true, null, current.recipeTitle);
    }

    public static void cancelForManualInput() {
        if (session != null) stop(StopReason.CANCELLED);
    }

    private static @Nullable StopReason lastStopReason;
    private static @Nullable Component lastRecipeTitle;

    private static void fill(Session current, Player player) {
        RecipeTreeAutoCraftService.Result result = RecipeTreeAutoCraftService.craftFirstAvailable(current.context);
        current.recipeTitle = result.recipeTitle();
        if (result.outcome() != RecipeTreeAutoCraftService.Outcome.TRANSFERRED) {
            stop(StopReason.TRANSFER_FAILED);
            return;
        }
        current.phase = Phase.WAIT_OUTPUT;
        current.waitTicks = 0;
    }

    private static void awaitOutput(Session current, Player player) {
        Slot output = outputSlot(player.containerMenu, player);
        if (output == null) {
            stop(StopReason.NO_OUTPUT);
            return;
        }
        if (output.getItem().isEmpty()) return;
        current.outputSlot = output.index;
        current.phase = Phase.TAKE_OUTPUT;
        current.waitTicks = 0;
    }

    private static void takeOutput(Session current, Player player) {
        if (!player.containerMenu.getCarried().isEmpty()) {
            stop(StopReason.NO_SPACE);
            return;
        }
        click(player, current.outputSlot);
        current.phase = Phase.STORE_CURSOR;
        current.waitTicks = 0;
    }

    private static void storeCursor(Session current, Player player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            nextOperation(current);
            return;
        }
        Slot target = playerSlotFor(player.containerMenu.slots, player, carried);
        if (target == null) {
            stop(StopReason.NO_SPACE);
            return;
        }
        click(player, target.index);
        current.phase = Phase.WAIT_STORE;
        current.waitTicks = 0;
    }

    private static void awaitStored(Session current, Player player) {
        if (player.containerMenu.getCarried().isEmpty()) {
            nextOperation(current);
        }
    }

    private static void nextOperation(Session current) {
        if (++current.operations >= MAX_OPERATIONS) {
            stop(StopReason.OPERATION_LIMIT);
            return;
        }
        current.phase = Phase.FILL;
        current.waitTicks = 0;
    }

    private static void click(Player player, int slotIndex) {
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slotIndex, 0,
                ClickType.PICKUP, player);
    }

    private static @Nullable Slot outputSlot(AbstractContainerMenu menu, Player player) {
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot && slot.mayPickup(player)) return slot;
        }
        for (Slot slot : menu.slots) {
            if (!slot.getItem().isEmpty() && !slot.mayPlace(slot.getItem()) && slot.mayPickup(player)) return slot;
        }
        return null;
    }

    private static @Nullable Slot playerSlotFor(List<Slot> slots, Player player, ItemStack carried) {
        Slot empty = null;
        for (Slot slot : slots) {
            if (slot.container != player.getInventory() || !slot.mayPlace(carried)) continue;
            ItemStack existing = slot.getItem();
            if (ItemStack.isSameItemSameComponents(existing, carried)
                    && existing.getCount() + carried.getCount() <= Math.min(slot.getMaxStackSize(), carried.getMaxStackSize())) {
                return slot;
            }
            if (empty == null && existing.isEmpty()) empty = slot;
        }
        return empty;
    }

    private static void stop(StopReason reason) {
        Session current = session;
        lastStopReason = reason;
        lastRecipeTitle = current == null ? lastRecipeTitle : current.recipeTitle;
        session = null;
    }

    private enum Phase {
        FILL,
        WAIT_OUTPUT,
        TAKE_OUTPUT,
        STORE_CURSOR,
        WAIT_STORE
    }

    private static final class Session {
        private final RecipeTreeRootContext context;
        private final int containerId;
        private Phase phase = Phase.FILL;
        private int waitTicks;
        private int operations;
        private int outputSlot = -1;
        private @Nullable Component recipeTitle;

        private Session(RecipeTreeRootContext context, int containerId) {
            this.context = context;
            this.containerId = containerId;
        }
    }
}