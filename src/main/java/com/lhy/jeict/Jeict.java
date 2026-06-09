package com.lhy.jeict;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Jeict.MODID)
public class Jeict {
    public static final String MODID = "jeict";

    public Jeict(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
