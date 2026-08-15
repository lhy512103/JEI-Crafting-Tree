package com.lhy.jeict.api;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.client.ClientMenuInventoryProviders;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Client-only registry for the one authoritative non-player inventory view of the currently open menu.
 *
 * <p>Providers are ordered by priority then id. Only the first matching provider is queried, so a network-backed
 * menu must replace, rather than supplement, the generic slot scanner. Register from client setup on the client
 * thread and retain the returned handle for reload/shutdown.
 */
public final class CraftingTreeMenuInventorySources {
    private CraftingTreeMenuInventorySources() {
    }

    public static ApiRegistration register(MenuInventorySource source) {
        return ClientMenuInventoryProviders.register(source);
    }

    public static @Nullable MenuInventorySource select(AbstractContainerMenu menu) {
        return ClientMenuInventoryProviders.select(menu);
    }

    public static List<MenuInventorySource> sources() {
        return ClientMenuInventoryProviders.providers();
    }
}