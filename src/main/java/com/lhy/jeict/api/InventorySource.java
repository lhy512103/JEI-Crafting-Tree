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

    default boolean isAvailable() {
        return true;
    }

    default long version() {
        return 0L;
    }

    List<InventoryAmount> snapshot();
}
