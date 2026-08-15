package com.lhy.jeict.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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

/**
 * 玩家记的"配方选择"与"替代品选择"持久化。
 *
 * <p>路径 2：原实现用 {@code levelKey + itemId + "|" + stack.getTag()} 拼注 Properties key，
 * 存在三个问题：
 * <ol>
 *   <li>{@link CompoundTag#toString()} 包含换行 / 等号 / 引号，作为 Properties key 不稳定也不安全；</li>
 *   <li>维度前缀仅在 {@code minecraft.level != null} 时拼接，
 *       导致玩家"进入世界的瞬间"和"完全加载后"的 key 不一致，记忆会闪烁；</li>
 *   <li>每次 {@link #remember} 同步刷盘，频繁切节点会震 IO。</li>
 * </ol>
 * 本实现：
 * <ul>
 *   <li>维度前缀恒定（{@code level == null} 时回退占位符 {@code __loading__}）；</li>
 *   <li>NBT 走 {@code Integer.toHexString(tag.hashCode())}，避免 toString 不稳定性；</li>
 *   <li>配方记忆不再按维度分桶（同一连接的配方表是统一的），只有替代品选择保留维度；</li>
 *   <li>{@link #remember} / {@link #rememberAlternative} 仅置 dirty，由 {@link #flush()} 批量落盘；
 *       flush 接入客户端 tick 末尾或退出钩子。调用方若需要立即落盘可显式调用 flush。</li>
 * </ul>
 */
public final class RecipeSelectionMemory {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("jeict-recipe-memory.properties");
    private static final String LOADING_PLACEHOLDER = "__loading__";
    private static final Properties SELECTIONS = new Properties();
    private static boolean loaded;
    private static boolean dirty;

    private RecipeSelectionMemory() {
    }

    public static Optional<ResourceLocation> selectedRecipe(ItemStack stack) {
        load();
        String value = SELECTIONS.getProperty("recipe." + recipeKey(stack));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }

    public static void remember(ItemStack stack, ResourceLocation recipeId) {
        load();
        SELECTIONS.setProperty("recipe." + recipeKey(stack), recipeId.toString());
        markDirty();
    }

    public static Optional<ItemStack> selectedAlternative(ItemStack stack) {
        load();
        String value = SELECTIONS.getProperty("alternative." + alternativeKey(stack));
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
        SELECTIONS.setProperty("alternative." + alternativeKey(stack), itemId.toString());
        markDirty();
    }

    /** 批量落盘：由调用方在 tick 末尾或退出时触发。 */
    public static void flush() {
        if (!loaded || !dirty) {
            return;
        }
        dirty = false;
        saveNow();
    }

    /**
     * 立即写入磁盘并在其后清 dirty。
     * <p>保留同步入口供旧调用方迁移；新调用方应优先 {@link #markDirty()} + 定期 {@link #flush()}。
     */
    private static void saveNow() {
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream stream = Files.newOutputStream(PATH)) {
                SELECTIONS.store(stream, "JEICT remembered recipe selections");
            }
        } catch (IOException ignored) {
        }
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

    private static void markDirty() {
        dirty = true;
    }

    /**
     * 配方选择 key：不按维度分桶——同一连接的配方表是统一的。
     * <p>NBT 用 {@code Integer.toHexString(tag.hashCode())} 避免 toString 不稳定。
     */
    private static String recipeKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId + "|" + tagHash(stack);
    }

    /**
     * 替代品选择 key：保留维度前缀（不同维度的"可替代物品集"可能不同，比如模组限定 overworld 才有的物品）。
     * <p>维度前缀恒定：{@code level == null} 时回退占位符 {@code __loading__}，
     * 避免"加载中"与"加载完成"产生两套 key 导致记忆闪烁。
     */
    private static String alternativeKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String levelKey = currentLevelKey();
        return levelKey + "|" + itemId + "|" + tagHash(stack);
    }

    private static String currentLevelKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return LOADING_PLACEHOLDER;
        }
        return minecraft.level.dimension().location().toString();
    }

    private static String tagHash(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return "";
        }
        return Integer.toHexString(tag.hashCode());
    }
}
