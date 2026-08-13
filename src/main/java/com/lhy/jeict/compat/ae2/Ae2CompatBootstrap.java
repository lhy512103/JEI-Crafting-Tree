package com.lhy.jeict.compat.ae2;

import net.neoforged.fml.ModList;

/** Keeps optional AE2 classes out of the class-loading path when AE2 is absent. */
public final class Ae2CompatBootstrap {
    private Ae2CompatBootstrap() {
    }

    public static void registerIfLoaded() {
        if (ModList.get().isLoaded("ae2")) {
            Ae2ClientMenuInventoryProvider.register();
            com.lhy.jeict.client.CraftingResultInteractions.register(new Ae2CraftingResultInteraction());
        }
    }
}