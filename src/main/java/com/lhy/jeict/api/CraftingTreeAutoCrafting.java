package com.lhy.jeict.api;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.client.RecipeTreeAutoCraftSession;

/**
 * Read-only controller for the built-in JEI transfer based auto-crafting session.
 *
 * <p>Client thread only. It never moves items without an already open, compatible crafting menu and a currently
 * visible JEICT recipe tree. Calling {@link #cancel()} is safe when no session is active.
 */
public final class CraftingTreeAutoCrafting {
    private CraftingTreeAutoCrafting() {
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

    public record Status(boolean running, @Nullable StopReason stopReason, @Nullable String recipeTitle) {
    }

    public static Status status() {
        RecipeTreeAutoCraftSession.Status status = RecipeTreeAutoCraftSession.status();
        return new Status(status.running(), map(status.stopReason()),
                status.recipeTitle() == null ? null : status.recipeTitle().getString());
    }

    public static void cancel() {
        RecipeTreeAutoCraftSession.cancelForManualInput();
    }

    private static @Nullable StopReason map(@Nullable RecipeTreeAutoCraftSession.StopReason reason) {
        return reason == null ? null : StopReason.valueOf(reason.name());
    }
}