package com.lhy.jeict.planning;

import java.util.Map;

/** Immutable quantities supplied by player inventory, an open menu, storage networks, or integrations. */
public record InventorySnapshot(Map<MaterialKey, Long> amounts) {
    public static final InventorySnapshot EMPTY = new InventorySnapshot(Map.of());

    public InventorySnapshot {
        amounts = amounts == null ? Map.of() : Map.copyOf(amounts);
    }

    public long amount(MaterialKey key) {
        return Math.max(0L, amounts.getOrDefault(key, 0L));
    }
}
