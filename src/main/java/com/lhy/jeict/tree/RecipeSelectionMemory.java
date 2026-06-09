package com.lhy.jeict.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class RecipeSelectionMemory {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("jeict-recipe-memory.properties");
    private static final Properties SELECTIONS = new Properties();
    private static boolean loaded;

    private RecipeSelectionMemory() {
    }

    public static Optional<ResourceLocation> selectedRecipe(ItemStack stack) {
        load();
        String value = SELECTIONS.getProperty("recipe." + key(stack));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }

    public static void remember(ItemStack stack, ResourceLocation recipeId) {
        load();
        SELECTIONS.setProperty("recipe." + key(stack), recipeId.toString());
        save();
    }

    public static Optional<ItemStack> selectedAlternative(ItemStack stack) {
        load();
        String value = SELECTIONS.getProperty("alternative." + key(stack));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
    }

    public static void rememberAlternative(ItemStack stack, ItemStack alternative) {
        load();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(alternative.getItem());
        SELECTIONS.setProperty("alternative." + key(stack), itemId.toString());
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(PATH)) {
            return;
        }
        try (InputStream stream = Files.newInputStream(PATH)) {
            SELECTIONS.load(stream);
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream stream = Files.newOutputStream(PATH)) {
                SELECTIONS.store(stream, "JEICT remembered recipe selections");
            }
        } catch (IOException ignored) {
        }
    }

    private static String key(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String levelKey = "";
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            levelKey = minecraft.level.dimension().location().toString() + "|";
        }
        return levelKey + itemId + "|" + (stack.getTag() == null ? "" : stack.getTag());
    }
}
