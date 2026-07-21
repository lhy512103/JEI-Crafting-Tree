package com.lhy.jeict.client;

import com.lhy.jeict.api.CraftingTreeInventorySources;
import com.lhy.jeict.planning.InventorySnapshot;

/** Shared versioned inventory cache used by planner and UI projections. */
public final class ClientInventorySnapshotCache {
    private static long version = Long.MIN_VALUE;
    private static InventorySnapshot snapshot = InventorySnapshot.EMPTY;

    private ClientInventorySnapshotCache() {}

    public static synchronized InventorySnapshot get() {
        long current = CraftingTreeInventorySources.combinedVersion();
        if (current != version) {
            snapshot = CraftingTreeInventorySources.aggregate();
            version = current;
        }
        return snapshot;
    }

    public static synchronized long version() {
        get();
        return version;
    }

    public static synchronized void invalidate() {
        version = Long.MIN_VALUE;
        snapshot = InventorySnapshot.EMPTY;
    }
}
