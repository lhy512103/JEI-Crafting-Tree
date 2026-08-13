package com.lhy.jeict.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.lhy.jeict.network.BatchCraftResultPayload;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.debug.AutoCraftDebug;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only execution loop for a currently open crafting menu. JEI remains responsible for
 * material transfer; the menu's quick-move protocol performs as many crafts as it supports.
 */
public final class RecipeTreeAutoCraftSession {
    private static final int SYNC_TIMEOUT_TICKS = 200;

    private static @Nullable Session session;
    private static long nextRequestId;

    private RecipeTreeAutoCraftSession() {
    }

    public enum StopReason {
        COMPLETED,
        CANCELLED,
        MENU_CHANGED,
        NO_OUTPUT,
        NO_SPACE,
        TRANSFER_FAILED,
        SYNC_TIMEOUT
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
        if (++current.waitTicks > SYNC_TIMEOUT_TICKS && current.phase != Phase.RETRY) {
            current.phase = Phase.RETRY;
            current.waitTicks = 0;
            return;
        }

        switch (current.phase) {
            case FILL -> fill(current, player);
            case RETRY -> retry(current, player);
            case WAIT_OUTPUT -> awaitOutput(current, player);
            case TAKE_OUTPUT -> takeOutput(current, player);
            case WAIT_CURSOR -> awaitCursor(current, player);
            case WAIT_BATCH -> { }
            case WAIT_DIRECT_TAKE -> awaitDirectTake(current, player);
            case STORE_CURSOR -> storeCursor(current, player);
            case WAIT_STORE -> awaitStored(current, player);
        }
    }

    public static Status status() {
        Session current = session;
        return current == null ? new Status(false, lastStopReason, lastRecipeTitle)
                : new Status(true, null, current.recipeTitle);
    }

    public static void handleBatchResult(BatchCraftResultPayload result) {
        Session current = session;
        if (current == null || current.phase != Phase.WAIT_BATCH
                || current.pendingRequestId != result.requestId()
                || current.containerId != result.containerId()) return;
        current.pendingRequestId = 0L;
        if (!result.accepted() || result.crafted() <= 0) {
            current.phase = Phase.RETRY;
            current.waitTicks = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.containerMenu.getCarried().isEmpty()) {
            stop(player == null ? StopReason.MENU_CHANGED : StopReason.NO_SPACE);
            return;
        }
        applyBatchInventoryChanges(current.recipe, result.crafted());
        ClientInventorySnapshotCache.invalidate();
        if (current.specializedOutput != null) current.specializedOutput.batchCompleted();
        nextOperation(current);
        if (session == current) fill(current, player);
    }

    public static void cancelForManualInput() {
        if (session != null) stop(StopReason.CANCELLED);
    }

    private static @Nullable StopReason lastStopReason;
    private static @Nullable Component lastRecipeTitle;

    private static void fill(Session current, Player player) {
        RecipeTreeAutoCraftService.Result result = RecipeTreeAutoCraftService.craftFirstAvailable(current.context);
        current.recipeTitle = result.recipeTitle();
        current.expectedOutput = result.expectedOutput();
        current.recipe = result.recipe();
        current.specializedOutput = null;
        current.outputSlot = -1;
        if (result.outcome() == RecipeTreeAutoCraftService.Outcome.COMPLETED) {
            stop(StopReason.COMPLETED);
            return;
        }
        if (result.outcome() != RecipeTreeAutoCraftService.Outcome.TRANSFERRED) {
            AutoCraftDebug.log("fill outcome={} title={}", result.outcome(),
                    result.recipeTitle() == null ? "" : result.recipeTitle().getString());
            current.phase = Phase.RETRY;
            current.waitTicks = 0;
            return;
        }
        current.phase = Phase.WAIT_OUTPUT;
        current.waitTicks = 0;
    }

    private static void retry(Session current, Player player) {
        if (++current.retryDelayTicks < 5) return;
        current.retryDelayTicks = 0;
        ClientInventorySnapshotCache.invalidate();
        current.phase = Phase.FILL;
        current.waitTicks = 0;
    }

    private static void awaitOutput(Session current, Player player) {
        CraftingResultInteractions.Resolved specialized = CraftingResultInteractions.find(
                player.containerMenu, player, current.expectedOutput);
        if (specialized != null) {
            current.specializedOutput = specialized;
            current.outputSlot = specialized.match().slot().index;
            long requestId = ++nextRequestId;
            if (specialized.executeBatch(player, requestId, current.expectedOutput)) {
                current.pendingRequestId = requestId;
                current.phase = Phase.WAIT_BATCH;
                current.waitTicks = 0;
                return;
            }
            current.phase = Phase.TAKE_OUTPUT;
            current.waitTicks = 0;
            return;
        }

        Slot output = vanillaOutputSlot(player.containerMenu, player, current.expectedOutput);
        if (output == null && net.neoforged.fml.ModList.get().isLoaded("sophisticatedcore")) {
            output = com.lhy.jeict.compat.sophisticated.SophisticatedCraftingClient.findResultSlot(
                    player.containerMenu, player, current.expectedOutput);
        }
        if (output == null || output.getItem().isEmpty()) return;
        current.outputSlot = output.index;
        current.phase = Phase.TAKE_OUTPUT;
        current.waitTicks = 0;
    }

    private static void takeOutput(Session current, Player player) {
        if (!player.containerMenu.getCarried().isEmpty()) {
            stop(StopReason.NO_SPACE);
            return;
        }
        if (current.specializedOutput != null) {
            current.playerOutputBefore = playerInventoryAmount(player, current.expectedOutput);
            current.specializedOutput.take(player);
            current.phase = current.specializedOutput.match().deliversToPlayerInventory()
                    ? Phase.WAIT_DIRECT_TAKE
                    : Phase.WAIT_STORE;
        } else {
            quickMove(player, current.outputSlot);
            current.phase = Phase.RETRY;
        }
        current.waitTicks = 0;
    }

    private static void awaitCursor(Session current, Player player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) return;
        Slot containerTarget = containerSlotFor(player.containerMenu.slots, player, carried, current.outputSlot);
        if (containerTarget != null) {
            click(player, containerTarget.index);
            current.phase = Phase.WAIT_STORE;
            current.waitTicks = 0;
            return;
        }
        current.phase = Phase.STORE_CURSOR;
        current.waitTicks = 0;
        storeCursor(current, player);
    }

    private static void awaitDirectTake(Session current, Player player) {
        if (playerInventoryAmount(player, current.expectedOutput) > current.playerOutputBefore) {
            ClientInventorySnapshotCache.invalidate();
            nextOperation(current);
        }
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
            ClientInventorySnapshotCache.invalidate();
            nextOperation(current);
        }
    }

    private static void applyBatchInventoryChanges(@Nullable RecipeTreeRecipeViewModel recipe, int craftedAmount) {
        if (recipe == null || craftedAmount <= 0) return;
        long crafts = ceilDiv(craftedAmount, recipe.primaryOutputAmount());
        Map<MaterialKey, Long> changes = new LinkedHashMap<>();
        MaterialKey outputKey = RecipeTreeAutoCraftService.materialKey(
                recipe.primaryOutputIngredient(), recipe.primaryOutput());
        changes.merge(outputKey, (long) craftedAmount, RecipePlanSolver::saturatedAdd);
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            if (!input.consumed()) continue;
            MaterialKey inputKey = RecipeTreeAutoCraftService.materialKey(input.displayIngredient(), input.displayStack());
            long consumed = saturatedMultiply(input.longAmount(), crafts);
            changes.merge(inputKey, -consumed, RecipeTreeAutoCraftSession::saturatedSignedAdd);
        }
        ClientInventorySnapshotCache.applyExpectedChanges(changes);
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0L) return 0L;
        long safeDenominator = Math.max(1L, denominator);
        return 1L + (numerator - 1L) / safeDenominator;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedSignedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static void nextOperation(Session current) {
        current.operations++;
        current.phase = Phase.FILL;
        current.waitTicks = 0;
    }

    private static void click(Player player, int slotIndex) {
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slotIndex, 0,
                ClickType.PICKUP, player);
    }

    private static void quickMove(Player player, int slotIndex) {
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slotIndex, 0,
                ClickType.QUICK_MOVE, player);
    }

    private static @Nullable Slot vanillaOutputSlot(AbstractContainerMenu menu, Player player,
            ItemStack expectedOutput) {
        if (expectedOutput == null || expectedOutput.isEmpty()) return null;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (slot.container != player.getInventory() && slot.mayPickup(player)
                    && ItemStack.isSameItemSameComponents(stack, expectedOutput)) {
                return slot;
            }
        }
        return null;
    }

    private static long playerInventoryAmount(Player player, ItemStack expectedOutput) {
        long amount = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, expectedOutput)) amount += stack.getCount();
        }
        return amount;
    }

    private static @Nullable Slot containerSlotFor(List<Slot> slots, Player player, ItemStack carried,
            int outputSlot) {
        for (Slot slot : slots) {
            if (slot.index == outputSlot || slot.container == player.getInventory() || !slot.mayPlace(carried)) continue;
            ItemStack existing = slot.getItem();
            if (ItemStack.isSameItemSameComponents(existing, carried)
                    && existing.getCount() + carried.getCount() <= Math.min(slot.getMaxStackSize(), carried.getMaxStackSize())) {
                return slot;
            }
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
        RETRY,
        WAIT_OUTPUT,
        TAKE_OUTPUT,
        WAIT_CURSOR,
        WAIT_BATCH,
        WAIT_DIRECT_TAKE,
        STORE_CURSOR,
        WAIT_STORE
    }

    private static final class Session {
        private final RecipeTreeRootContext context;
        private final int containerId;
        private Phase phase = Phase.FILL;
        private int waitTicks;
        private int retryDelayTicks;
        private int operations;
        private int outputSlot = -1;
        private long pendingRequestId;
        private long playerOutputBefore;
        private @Nullable Component recipeTitle;
        private @Nullable RecipeTreeRecipeViewModel recipe;
        private @Nullable CraftingResultInteractions.Resolved specializedOutput;
        private ItemStack expectedOutput = ItemStack.EMPTY;

        private Session(RecipeTreeRootContext context, int containerId) {
            this.context = context;
            this.containerId = containerId;
        }
    }
}