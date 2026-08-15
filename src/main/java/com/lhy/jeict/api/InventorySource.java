package com.lhy.jeict.api;

import java.util.List;

/**
 * Extensible inventory provider for player, menu, storage-network, or mod-specific stock.
 * Implementations should return immutable snapshots and increment {@link #version()} only when content changes.
 */
public interface InventorySource {
    String id();

    default int priority() {
        return 0;
    }

    /**
     * Logical storage identity used to prevent duplicate aggregation. Sources in the same group are mutually
     * exclusive; the highest-priority available source is authoritative. The default keeps legacy sources separate.
     */
    default String authorityGroup() {
        return id();
    }

    default boolean isAvailable() {
        return true;
    }

    default long version() {
        return 0L;
    }

    List<InventoryAmount> snapshot();
}
