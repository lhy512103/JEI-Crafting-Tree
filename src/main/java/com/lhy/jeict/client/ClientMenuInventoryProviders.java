package com.lhy.jeict.client;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.api.ApiRegistration;
import com.lhy.jeict.api.MenuInventorySource;

import net.minecraft.world.inventory.AbstractContainerMenu;

/** Internal selector backing the public client-only menu inventory extension point. */
public final class ClientMenuInventoryProviders {
    private static final CopyOnWriteArrayList<MenuInventorySource> PROVIDERS = new CopyOnWriteArrayList<>();

    private ClientMenuInventoryProviders() {
    }

    /** Legacy built-in registration alias. */
    public static void register(ClientMenuInventoryProvider provider) {
        register((MenuInventorySource) provider);
    }

    public static ApiRegistration register(MenuInventorySource provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) return ApiRegistration.NOOP;
        PROVIDERS.removeIf(existing -> provider.id().equals(existing.id()));
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(MenuInventorySource::priority).reversed()
                .thenComparing(MenuInventorySource::id));
        return () -> PROVIDERS.remove(provider);
    }

    public static @Nullable MenuInventorySource select(AbstractContainerMenu menu) {
        if (menu == null) return null;
        for (MenuInventorySource provider : PROVIDERS) {
            try {
                if (provider.supports(menu)) return provider;
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    public static List<MenuInventorySource> providers() {
        return List.copyOf(PROVIDERS);
    }
}
