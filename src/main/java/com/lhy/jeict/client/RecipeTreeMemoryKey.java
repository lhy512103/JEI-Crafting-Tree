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
import net.minecraftforge.fml.ModList;

/** Builds versioned, scope-aware keys for remembered recipe choices. */
public final class RecipeTreeMemoryKey {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static volatile String modpackFingerprint;
    private static volatile ScopeProfileCache scopeProfileCache;

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
        Scope scope = RecipeTreeConfig.MEMORY_SCOPE.get();
        String dimension = scope == Scope.WORLD
                ? (minecraft.level == null ? "no-world" : minecraft.level.dimension().location().toString())
                : "";
        String serverIdentity = serverIdentity(minecraft);
        ScopeProfileCache cached = scopeProfileCache;
        if (cached != null && cached.matches(scope, serverIdentity, dimension)) {
            return cached.profile();
        }
        String profile = switch (scope) {
            case GLOBAL -> "global";
            case SERVER -> serverProfile(minecraft);
            case WORLD -> "world:" + serverProfile(minecraft) + ":" + dimension;
        };
        scopeProfileCache = new ScopeProfileCache(scope, serverIdentity, dimension, profile);
        return profile;
    }

    private static String serverIdentity(Minecraft minecraft) {
        var remote = minecraft.getCurrentServer();
        if (remote != null) return "remote:" + remote.ip;
        var integrated = minecraft.getSingleplayerServer();
        if (integrated != null) return "integrated:" + System.identityHashCode(integrated);
        return "none";
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
            for (int i = 0; i < 8; i++) {
                int valueByte = digest[i] & 0xFF;
                result.append(HEX[valueByte >>> 4]).append(HEX[valueByte & 0x0F]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record ScopeProfileCache(Scope scope, String serverIdentity, String dimension, String profile) {
        private boolean matches(Scope requestedScope, String requestedServerIdentity, String requestedDimension) {
            return scope == requestedScope && serverIdentity.equals(requestedServerIdentity)
                    && dimension.equals(requestedDimension);
        }
    }

    public enum Scope { GLOBAL, SERVER, WORLD }
}
