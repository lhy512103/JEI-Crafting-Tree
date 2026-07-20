package com.lhy.jeict.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.mojang.logging.LogUtils;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

public final class RecipeTreeClientMemory {
    private static final Map<String, RecipeTreeRecipeViewModel> REMEMBERED_SELECTIONS = new HashMap<>();
    private static final Map<String, ResourceLocation> REMEMBERED_SELECTION_IDS = new HashMap<>();
    private static final Map<String, String> REMEMBERED_SELECTION_KEYS = new HashMap<>();
    private static final Map<ResourceLocation, Optional<RecipeTreeRecipeViewModel>> RECIPE_BY_ID_CACHE = new HashMap<>();
    private static final Set<String> COLLAPSED_SIGNATURES = new HashSet<>();
    private static final String MEMORY_FILE_NAME = JeiCraftingTreeMod.MOD_ID + "_recipe_tree_memory.properties";
    private static final String MEMORY_READING_ENABLED_KEY = "memory-reading-enabled";
    private static final String COLLAPSED_SIGNATURE_KEY_PREFIX = "collapsed-signature.";
    private static final String RECIPE_KEY_PREFIX = "recipe-key.";
    private static final Object SAVE_LOCK = new Object();
    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JEICT Recipe Memory Writer");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> pendingSave;
    private static boolean loadedFromDisk;
    private static boolean memoryReadingEnabled = true;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RecipeTreeClientMemory::flushPendingSave,
                "JEICT Recipe Memory Shutdown Writer"));
    }

    private RecipeTreeClientMemory() {
    }

    public static void rememberSelection(String signature, RecipeTreeRecipeViewModel recipe) {
        if (signature == null || signature.isBlank() || recipe == null) {
            return;
        }
        REMEMBERED_SELECTIONS.put(signature, recipe);
        ensureLoadedFromDisk();
        synchronized (SAVE_LOCK) {
            ResourceLocation recipeId = recipe.recipeId();
            if (recipeId != null) {
                REMEMBERED_SELECTION_IDS.put(signature, recipeId);
            } else {
                REMEMBERED_SELECTION_IDS.remove(signature);
            }
            REMEMBERED_SELECTION_KEYS.put(signature, recipeMemoryKey(recipe));
            scheduleSaveLocked();
        }
    }

    public static @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        return getRememberedSelection(signature, null);
    }

    public static @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature,
            @Nullable ITypedIngredient<?> outputFocus) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        RecipeTreeRecipeViewModel remembered = REMEMBERED_SELECTIONS.get(signature);
        if (remembered != null) {
            return remembered;
        }
        ensureLoadedFromDisk();
        ResourceLocation recipeId = REMEMBERED_SELECTION_IDS.get(signature);
        if (recipeId != null) {
            remembered = RECIPE_BY_ID_CACHE.computeIfAbsent(recipeId, RecipeTreeJeiLookup::findRecipeById).orElse(null);
            if (remembered != null) {
                REMEMBERED_SELECTIONS.put(signature, remembered);
                return remembered;
            }
        }
        String recipeKey = REMEMBERED_SELECTION_KEYS.get(signature);
        if (recipeKey == null || outputFocus == null) {
            return null;
        }
        for (RecipeTreeRecipeViewModel candidate : RecipeTreeJeiLookup.findRecipesByOutput(outputFocus)) {
            if (recipeKey.equals(recipeMemoryKey(candidate))) {
                REMEMBERED_SELECTIONS.put(signature, candidate);
                return candidate;
            }
        }
        return null;
    }

    public static void forgetSelection(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        REMEMBERED_SELECTIONS.remove(signature);
        ensureLoadedFromDisk();
        synchronized (SAVE_LOCK) {
            boolean changed = REMEMBERED_SELECTION_IDS.remove(signature) != null;
            changed |= REMEMBERED_SELECTION_KEYS.remove(signature) != null;
            if (changed) {
                scheduleSaveLocked();
            }
        }
    }

    public static void rememberCollapsed(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        ensureLoadedFromDisk();
        synchronized (SAVE_LOCK) {
            if (COLLAPSED_SIGNATURES.add(signature)) {
                scheduleSaveLocked();
            }
        }
    }

    public static void forgetCollapsed(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        ensureLoadedFromDisk();
        synchronized (SAVE_LOCK) {
            if (COLLAPSED_SIGNATURES.remove(signature)) {
                scheduleSaveLocked();
            }
        }
    }

    public static boolean isCollapsed(String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        ensureLoadedFromDisk();
        return COLLAPSED_SIGNATURES.contains(signature);
    }

    public static boolean isMemoryReadingEnabled() {
        ensureLoadedFromDisk();
        return memoryReadingEnabled;
    }

    public static void setMemoryReadingEnabled(boolean enabled) {
        ensureLoadedFromDisk();
        if (memoryReadingEnabled == enabled) {
            return;
        }
        synchronized (SAVE_LOCK) {
            memoryReadingEnabled = enabled;
            scheduleSaveLocked();
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
            if (MEMORY_READING_ENABLED_KEY.equals(key)) {
                memoryReadingEnabled = Boolean.parseBoolean(properties.getProperty(key, "true"));
                continue;
            }
            if (key.startsWith(COLLAPSED_SIGNATURE_KEY_PREFIX)) {
                if (Boolean.parseBoolean(properties.getProperty(key, "true"))) {
                    COLLAPSED_SIGNATURES.add(key.substring(COLLAPSED_SIGNATURE_KEY_PREFIX.length()));
                }
                continue;
            }
            if (key.startsWith(RECIPE_KEY_PREFIX)) {
                REMEMBERED_SELECTION_KEYS.put(key.substring(RECIPE_KEY_PREFIX.length()), properties.getProperty(key));
                continue;
            }
            ResourceLocation recipeId = ResourceLocation.tryParse(properties.getProperty(key));
            if (recipeId != null) {
                REMEMBERED_SELECTION_IDS.put(key, recipeId);
            }
        }
    }

    private static void scheduleSaveLocked() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = SAVE_EXECUTOR.schedule(RecipeTreeClientMemory::savePendingToDisk, 200L, TimeUnit.MILLISECONDS);
    }

    public static void flushPendingSave() {
        ScheduledFuture<?> saveToRun;
        synchronized (SAVE_LOCK) {
            saveToRun = pendingSave;
            if (saveToRun == null) {
                return;
            }
            saveToRun.cancel(false);
        }
        savePendingToDisk();
    }

    private static void savePendingToDisk() {
        Map<String, ResourceLocation> selections;
        Map<String, String> selectionKeys;
        Set<String> collapsedSignatures;
        boolean readingEnabled;
        synchronized (SAVE_LOCK) {
            selections = Map.copyOf(REMEMBERED_SELECTION_IDS);
            selectionKeys = Map.copyOf(REMEMBERED_SELECTION_KEYS);
            collapsedSignatures = Set.copyOf(COLLAPSED_SIGNATURES);
            readingEnabled = memoryReadingEnabled;
            pendingSave = null;
        }
        saveToDisk(selections, selectionKeys, collapsedSignatures, readingEnabled);
    }

    private static void saveToDisk(Map<String, ResourceLocation> selections, Map<String, String> selectionKeys,
            Set<String> collapsedSignatures, boolean readingEnabled) {
        Path path = memoryPath();
        Properties properties = new Properties();
        properties.setProperty(MEMORY_READING_ENABLED_KEY, Boolean.toString(readingEnabled));
        for (Map.Entry<String, ResourceLocation> entry : selections.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue().toString());
        }
        for (Map.Entry<String, String> entry : selectionKeys.entrySet()) {
            properties.setProperty(RECIPE_KEY_PREFIX + entry.getKey(), entry.getValue());
        }
        for (String signature : collapsedSignatures) {
            properties.setProperty(COLLAPSED_SIGNATURE_KEY_PREFIX + signature, Boolean.TRUE.toString());
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

    private static String recipeMemoryKey(RecipeTreeRecipeViewModel recipe) {
        ResourceLocation recipeId = recipe.recipeId();
        if (recipeId != null) {
            return "id#" + recipeId;
        }
        ItemStack output = recipe.primaryOutput();
        String outputKey = output.isEmpty() ? "" : output.getItem().toString();
        return "view#" + recipe.title().getString() + "#" + recipe.primaryOutputCount() + "#" + outputKey;
    }

    private static Path memoryPath() {
        return FMLPaths.CONFIGDIR.get().resolve(MEMORY_FILE_NAME);
    }
}
