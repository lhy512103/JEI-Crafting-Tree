package com.lhy.jeict;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Jeict.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final ForgeConfigSpec SPEC = com.lhy.jeict.config.RecipeTreeConfig.SPEC;

    public static int maxTreeDepth() { return com.lhy.jeict.config.RecipeTreeConfig.maxTreeDepth(); }
    public static int maxTreeNodes() { return com.lhy.jeict.config.RecipeTreeConfig.maxTreeNodes(); }
    public static int maxCandidatesPerIngredient() { return com.lhy.jeict.config.RecipeTreeConfig.maxCandidatesPerIngredient(); }
    public static boolean showOnlyCraftableCandidates() { return com.lhy.jeict.config.RecipeTreeConfig.showOnlyCraftableCandidates(); }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        // 配置变更时清理缓存——下游直接调 getter 获取最新值，无需静态缓存
        com.lhy.jeict.tree.RecipeGraphCache.clear();
    }
}
