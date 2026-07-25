package com.lhy.jeict.api;

/**
 * JEI Crafting Tree 公开 API 的语义版本。
 *
 * <p>仅 {@code com.lhy.jeict.api} 中标为稳定的类型受兼容承诺保护。调用方应在客户端初始化时
 * 通过 {@link #isCompatibleWith(int)} 探测主版本，避免以反射访问内部实现。
 */
public final class JeiCraftingTreeApiVersion {
    public static final int MAJOR = 1;
    public static final int MINOR = 0;
    public static final String VERSION = MAJOR + "." + MINOR;

    private JeiCraftingTreeApiVersion() {
    }

    public static boolean isCompatibleWith(int requiredMajor) {
        return requiredMajor == MAJOR;
    }
}