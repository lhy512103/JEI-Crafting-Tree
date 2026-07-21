package com.lhy.jeict.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Formal client configuration for planning defaults and performance guardrails. */
public final class RecipeTreeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REMEMBER_SELECTIONS;
    public static final ModConfigSpec.BooleanValue AUTO_MERGE_MATERIALS;
    public static final ModConfigSpec.BooleanValue COMPUTE_QUANTITIES;
    public static final ModConfigSpec.BooleanValue AUTO_EXPAND_UNIQUE_RECIPES;
    public static final ModConfigSpec.BooleanValue SHOW_FLOATING_MATERIALS;
    public static final ModConfigSpec.EnumValue<com.lhy.jeict.client.RecipeTreeMemoryKey.Scope> MEMORY_SCOPE;
    public static final ModConfigSpec.ConfigValue<String> MEMORY_PROFILE;
    public static final ModConfigSpec.ConfigValue<String> PREFERRED_NAMESPACE;
    public static final ModConfigSpec.IntValue MAX_AUTO_EXPAND_STEPS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_RECIPE_LOOKUP_RESULTS;
    public static final ModConfigSpec.EnumValue<com.lhy.jeict.planning.SubstitutionStrategy> SUBSTITUTION_STRATEGY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
        SPEC = builder.build();
    }

    private RecipeTreeConfig() {
    }
}
