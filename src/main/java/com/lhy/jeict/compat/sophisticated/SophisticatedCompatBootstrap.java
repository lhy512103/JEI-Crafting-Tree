package com.lhy.jeict.compat.sophisticated;

import com.lhy.jeict.client.CraftingResultInteractions;

import net.minecraftforge.fml.ModList;

public final class SophisticatedCompatBootstrap {
    private SophisticatedCompatBootstrap() {}

    public static void registerIfLoaded() {
        if (ModList.get().isLoaded("sophisticatedcore")) {
            CraftingResultInteractions.register(new SophisticatedCraftingResultInteraction());
        }
    }
}
