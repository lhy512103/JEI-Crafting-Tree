package com.lhy.jeict.api;

import org.jetbrains.annotations.Nullable;

/**
 * 配方树 AE2 后端注册表。外部模组（如 AE2 Utility）在客户端初始化时注册自己的实现。
 */
public final class CraftingTreeBackends {
    private static volatile @Nullable CraftingTreeBackend backend;

    private CraftingTreeBackends() {
    }

    public static void register(CraftingTreeBackend backend) {
        CraftingTreeBackends.backend = backend;
    }

    /** 返回已注册后端，未注册时为 {@code null}（此时界面为纯规划树）。 */
    public static @Nullable CraftingTreeBackend get() {
        return backend;
    }

    public static boolean isPresent() {
        return backend != null;
    }
}
