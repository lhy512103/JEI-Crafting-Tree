package com.lhy.jeict;

import com.lhy.jeict.client.JeictClientBootstrap;
import com.lhy.jeict.network.JeictNetworking;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class JeiCraftingTreeMod {
    public static final String MOD_ID = "jeict";

    public JeiCraftingTreeMod(IEventBus modBus) {
        JeictNetworking.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            JeictClientBootstrap.initialize(modBus);
        }
    }
}
