package com.lhy.jeict;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Jeict.MODID)
public class Jeict {
    public static final String MODID = "jeict";
    private static final Logger LOGGER = LoggerFactory.getLogger(Jeict.class);

    public Jeict() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.lhy.jeict.config.RecipeTreeConfig.SPEC);
        new JeiCraftingTreeMod(FMLJavaModLoadingContext.get().getModEventBus());
        if (!ModList.get().isLoaded("jei")) {
            LOGGER.warn("JEI not detected — JEICT requires JEI to function. Keybind will be disabled.");
        }
    }
}
