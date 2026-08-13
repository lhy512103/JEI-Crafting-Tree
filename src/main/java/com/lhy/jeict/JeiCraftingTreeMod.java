package com.lhy.jeict;

import com.lhy.jeict.client.JeictClientBootstrap;
import com.lhy.jeict.network.JeictNetworking;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(JeiCraftingTreeMod.MOD_ID)
public final class JeiCraftingTreeMod {
    public static final String MOD_ID = "jeict";

    public JeiCraftingTreeMod(IEventBus modBus) {
        modBus.addListener(JeictNetworking::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            JeictClientBootstrap.initialize(modBus);
        }
    }
}