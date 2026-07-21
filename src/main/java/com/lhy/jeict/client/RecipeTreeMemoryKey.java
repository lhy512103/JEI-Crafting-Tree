package com.lhy.jeict.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Builds versioned, scope-aware keys for remembered recipe choices. */
public final class RecipeTreeMemoryKey {
    private static volatile String modpackFingerprint;

    private RecipeTreeMemoryKey() {}

    public static String of(RecipeTreeNodeViewModel parent, int inputIndex, RecipeTreeInputViewModel input,
            String ingredientSignature) {
        String parentId = parent == null ? "root" : parent.recipe().stableIdentity();
        String path = parent == null ? Integer.toString(inputIndex) : nodePath(parent) + "/" + inputIndex;
        return "v2#" + scopeProfile() + "#" + fingerprint() + "#" + parentId + "#" + path + "#"
                + ingredientSignature;
    }

    private static String nodePath(RecipeTreeNodeViewModel node) {
        StringBuilder path = new StringBuilder();
        for (RecipeTreeNodeViewModel cursor = node; cursor != null; cursor = cursor.parent()) {
            if (path.length() > 0) path.insert(0, '/');
            path.insert(0, cursor.recipe().stableIdentity());
        }
        return path.toString();
    }

    private static String scopeProfile() {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (RecipeTreeConfig.MEMORY_SCOPE.get()) {
            case GLOBAL -> "global";
            case SERVER -> serverProfile(minecraft);
            case WORLD -> {
                String dimension = minecraft.level == null ? "no-world" : minecraft.level.dimension().location().toString();
                yield "world:" + serverProfile(minecraft) + ":" + dimension;
            }
        };
    }

    private static String serverProfile(Minecraft minecraft) {
        var remote = minecraft.getCurrentServer();
        if (remote != null) return "server:" + remote.ip;
        var integrated = minecraft.getSingleplayerServer();
        if (integrated != null) {
            String worldPath = integrated.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().toString();
            return "singleplayer:" + shortSha256(worldPath);
        }
        return "singleplayer:unknown";
    }

    private static String fingerprint() {
        String configured = RecipeTreeConfig.MEMORY_PROFILE.get().trim();
        if (!configured.isEmpty()) return configured;
        String cached = modpackFingerprint;
        if (cached != null) return cached;
        String descriptor = ModList.get().getMods().stream()
                .sorted(Comparator.comparing(info -> info.getModId()))
                .map(info -> info.getModId() + "@" + info.getVersion())
                .collect(Collectors.joining("|"));
        modpackFingerprint = shortSha256(descriptor);
        return modpackFingerprint;
    }

    private static String shortSha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public enum Scope { GLOBAL, SERVER, WORLD }
}
