package com.lhy.jeict.debug;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/** Short-lived diagnostics for menu transfer failures; only called from an auto-craft attempt. */
public final class AutoCraftDebug {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AutoCraftDebug() {}

    public static void log(String message, Object... args) {
        LOGGER.info("[JEICT-AUTO] " + message, args);
    }
}