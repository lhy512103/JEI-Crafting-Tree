package com.lhy.jeict.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class RecipeTreeClientMemory {
    private static final Map<String, RecipeTreeRecipeViewModel> REMEMBERED_SELECTIONS = new HashMap<>();
    private static final Map<String, ResourceLocation> REMEMBERED_SELECTION_IDS = new HashMap<>();
    private static final String MEMORY_FILE_NAME = JeiCraftingTreeMod.MOD_ID + "_recipe_tree_memory.properties";
    private static boolean loadedFromDisk;

    private RecipeTreeClientMemory() {
    }

    public static void rememberSelection(String signature, RecipeTreeRecipeViewModel recipe) {
        if (signature == null || signature.isBlank() || recipe == null) {
            return;
        }
        REMEMBERED_SELECTIONS.put(signature, recipe);
        ResourceLocation recipeId = recipe.recipeId();
        if (recipeId != null) {
            ensureLoadedFromDisk();
            REMEMBERED_SELECTION_IDS.put(signature, recipeId);
            saveToDisk();
        }
    }

    public static @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        RecipeTreeRecipeViewModel remembered = REMEMBERED_SELECTIONS.get(signature);
        if (remembered != null) {
            return remembered;
        }
        ensureLoadedFromDisk();
        ResourceLocation recipeId = REMEMBERED_SELECTION_IDS.get(signature);
        if (recipeId == null) {
            return null;
        }
        remembered = RecipeTreeJeiLookup.findRecipeById(recipeId).orElse(null);
        if (remembered != null) {
            REMEMBERED_SELECTIONS.put(signature, remembered);
        }
        return remembered;
    }

    public static void forgetSelection(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        REMEMBERED_SELECTIONS.remove(signature);
        ensureLoadedFromDisk();
        if (REMEMBERED_SELECTION_IDS.remove(signature) != null) {
            saveToDisk();
        }
    }

    private static void ensureLoadedFromDisk() {
        if (loadedFromDisk) {
            return;
        }
        loadedFromDisk = true;
        Path path = memoryPath();
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ex) {
            LogUtils.getLogger().warn("Failed to load JEICT recipe tree memory from {}", path, ex);
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            ResourceLocation recipeId = ResourceLocation.tryParse(properties.getProperty(key));
            if (recipeId != null) {
                REMEMBERED_SELECTION_IDS.put(key, recipeId);
            }
        }
    }

    private static void saveToDisk() {
        Path path = memoryPath();
        if (path == null) {
            return;
        }
        Properties properties = new Properties();
        for (Map.Entry<String, ResourceLocation> entry : REMEMBERED_SELECTION_IDS.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue().toString());
        }
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "JEICT recipe tree remembered child recipe selections");
            }
        } catch (IOException ex) {
            LogUtils.getLogger().warn("Failed to save JEICT recipe tree memory to {}", path, ex);
        }
    }

    private static @Nullable Path memoryPath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath().resolve("config").resolve(MEMORY_FILE_NAME);
    }
}
