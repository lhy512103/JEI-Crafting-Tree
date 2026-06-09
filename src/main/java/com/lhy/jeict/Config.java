package com.lhy.jeict;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Jeict.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_TREE_DEPTH = BUILDER
            .defineInRange("maxTreeDepth", 8, 1, 32);
    private static final ForgeConfigSpec.IntValue MAX_TREE_NODES = BUILDER
            .defineInRange("maxTreeNodes", 256, 16, 4096);
    private static final ForgeConfigSpec.IntValue MAX_CANDIDATES_PER_INGREDIENT = BUILDER
            .defineInRange("maxCandidatesPerIngredient", 12, 1, 128);
    private static final ForgeConfigSpec.BooleanValue SHOW_ONLY_CRAFTABLE_CANDIDATES = BUILDER
            .define("showOnlyCraftableCandidates", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int maxTreeDepth;
    public static int maxTreeNodes;
    public static int maxCandidatesPerIngredient;
    public static boolean showOnlyCraftableCandidates;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        maxTreeDepth = MAX_TREE_DEPTH.get();
        maxTreeNodes = MAX_TREE_NODES.get();
        maxCandidatesPerIngredient = MAX_CANDIDATES_PER_INGREDIENT.get();
        showOnlyCraftableCandidates = SHOW_ONLY_CRAFTABLE_CANDIDATES.get();
    }
}
