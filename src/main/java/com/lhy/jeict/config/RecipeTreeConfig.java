package com.lhy.jeict.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Formal client configuration for planning defaults and performance guardrails. */
public final class RecipeTreeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue REMEMBER_SELECTIONS;
    public static final ForgeConfigSpec.BooleanValue AUTO_MERGE_MATERIALS;
    public static final ForgeConfigSpec.BooleanValue COMPUTE_QUANTITIES;
    public static final ForgeConfigSpec.BooleanValue AUTO_EXPAND_UNIQUE_RECIPES;
    public static final ForgeConfigSpec.BooleanValue SHOW_FLOATING_MATERIALS;
    public static final ForgeConfigSpec.EnumValue<com.lhy.jeict.client.RecipeTreeMemoryKey.Scope> MEMORY_SCOPE;
    public static final ForgeConfigSpec.ConfigValue<String> MEMORY_PROFILE;
    public static final ForgeConfigSpec.ConfigValue<String> PREFERRED_NAMESPACE;
    public static final ForgeConfigSpec.IntValue MAX_AUTO_EXPAND_STEPS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_RECIPE_LOOKUP_RESULTS;
    public static final ForgeConfigSpec.EnumValue<com.lhy.jeict.planning.SubstitutionStrategy> SUBSTITUTION_STRATEGY;
    private static final ForgeConfigSpec.IntValue MAX_TREE_DEPTH;
    private static final ForgeConfigSpec.IntValue MAX_TREE_NODES;
    private static final ForgeConfigSpec.IntValue MAX_CANDIDATES_PER_INGREDIENT;
    private static final ForgeConfigSpec.BooleanValue SHOW_ONLY_CRAFTABLE_CANDIDATES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("planning");
        REMEMBER_SELECTIONS = builder.define("rememberSelections", true);
        AUTO_MERGE_MATERIALS = builder.define("autoMergeMaterials", true);
        COMPUTE_QUANTITIES = builder.define("computeQuantities", true);
        AUTO_EXPAND_UNIQUE_RECIPES = builder.define("autoExpandUniqueRecipes", false);
        SUBSTITUTION_STRATEGY = builder.defineEnum("substitutionStrategy",
                com.lhy.jeict.planning.SubstitutionStrategy.LOCKED);
        PREFERRED_NAMESPACE = builder.define("preferredNamespace", "");
        MEMORY_SCOPE = builder.defineEnum("memoryScope", com.lhy.jeict.client.RecipeTreeMemoryKey.Scope.SERVER);
        MEMORY_PROFILE = builder.define("memoryProfile", "");
        builder.pop();
        builder.push("performance");
        SHOW_FLOATING_MATERIALS = builder.define("showFloatingMaterials", true);
        MAX_AUTO_EXPAND_STEPS_PER_TICK = builder.defineInRange("maxAutoExpandStepsPerTick", 32, 1, 512);
        MAX_RECIPE_LOOKUP_RESULTS = builder.defineInRange("maxRecipeLookupResults", 512, 16, 8192);
        builder.pop();
        builder.push("legacy");
        MAX_TREE_DEPTH = builder.defineInRange("maxTreeDepth", 8, 1, 32);
        MAX_TREE_NODES = builder.defineInRange("maxTreeNodes", 256, 16, 4096);
        MAX_CANDIDATES_PER_INGREDIENT = builder.defineInRange("maxCandidatesPerIngredient", 12, 1, 128);
        SHOW_ONLY_CRAFTABLE_CANDIDATES = builder.define("showOnlyCraftableCandidates", false);
        builder.pop();
        SPEC = builder.build();
    }

    private RecipeTreeConfig() {
    }

    public static int maxTreeDepth() {
        return MAX_TREE_DEPTH.get();
    }

    public static int maxTreeNodes() {
        return MAX_TREE_NODES.get();
    }

    public static int maxCandidatesPerIngredient() {
        return MAX_CANDIDATES_PER_INGREDIENT.get();
    }

    public static boolean showOnlyCraftableCandidates() {
        return SHOW_ONLY_CRAFTABLE_CANDIDATES.get();
    }
}
