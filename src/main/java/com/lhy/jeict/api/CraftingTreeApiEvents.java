package com.lhy.jeict.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.lhy.jeict.client.RecipeTreeOverviewScreen;

import net.minecraft.client.Minecraft;

/**
 * Client-thread notifications for externally observable JEICT state.
 *
 * <p>Listeners are called after the corresponding state changes, never from a background planning worker.
 * Exceptions are isolated so one integration cannot prevent other listeners from receiving updates.
 */
public final class CraftingTreeApiEvents {
    private static final CopyOnWriteArrayList<Consumer<Boolean>> WORKSPACE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<Long>> INVENTORY_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<CraftingTreeAutoCrafting.Status>> AUTO_CRAFT_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean workspaceOpen;
    private static long inventoryVersion = Long.MIN_VALUE;
    private static CraftingTreeAutoCrafting.Status autoCraftStatus = new CraftingTreeAutoCrafting.Status(false, null, null);

    private CraftingTreeApiEvents() {
    }

    public static ApiRegistration onWorkspaceChanged(Consumer<Boolean> listener) {
        return register(WORKSPACE_LISTENERS, listener);
    }

    public static ApiRegistration onInventoryVersionChanged(Consumer<Long> listener) {
        return register(INVENTORY_LISTENERS, listener);
    }

    public static ApiRegistration onAutoCraftStatusChanged(Consumer<CraftingTreeAutoCrafting.Status> listener) {
        return register(AUTO_CRAFT_LISTENERS, listener);
    }

    /** Internal client-tick bridge; integrations must not call this method. */
    public static void onClientTick() {
        boolean open = Minecraft.getInstance().screen instanceof RecipeTreeOverviewScreen;
        if (open != workspaceOpen) {
            workspaceOpen = open;
            notify(WORKSPACE_LISTENERS, open);
        }

        long nextInventoryVersion = CraftingTreeInventorySources.combinedVersion();
        if (nextInventoryVersion != inventoryVersion) {
            inventoryVersion = nextInventoryVersion;
            notify(INVENTORY_LISTENERS, nextInventoryVersion);
        }

        CraftingTreeAutoCrafting.Status nextAutoCraftStatus = CraftingTreeAutoCrafting.status();
        if (!nextAutoCraftStatus.equals(autoCraftStatus)) {
            autoCraftStatus = nextAutoCraftStatus;
            notify(AUTO_CRAFT_LISTENERS, nextAutoCraftStatus);
        }
    }

    private static <T> ApiRegistration register(CopyOnWriteArrayList<Consumer<T>> listeners, Consumer<T> listener) {
        if (listener == null) return ApiRegistration.NOOP;
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private static <T> void notify(CopyOnWriteArrayList<Consumer<T>> listeners, T value) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(value);
            } catch (RuntimeException ignored) {
            }
        }
    }
}