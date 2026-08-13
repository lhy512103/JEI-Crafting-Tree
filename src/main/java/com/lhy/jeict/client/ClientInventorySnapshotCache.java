package com.lhy.jeict.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.lhy.jeict.api.CraftingTreeInventorySources;
import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Shared versioned inventory cache used by planner and UI projections. */
public final class ClientInventorySnapshotCache {
    private static long sourceVersion = Long.MIN_VALUE;
    private static int menuIdentity;
    private static long projectionVersion;
    private static InventorySnapshot sourceSnapshot = InventorySnapshot.EMPTY;
    private static InventorySnapshot projectedSnapshot = InventorySnapshot.EMPTY;
    private static final Map<MaterialKey, PendingAmount> pendingAmounts = new LinkedHashMap<>();

    private ClientInventorySnapshotCache() {}

    public static synchronized InventorySnapshot get() {
        AbstractContainerMenu menu = currentMenu();
        int currentMenuIdentity = menu == null ? 0 : System.identityHashCode(menu);
        if (currentMenuIdentity != menuIdentity) {
            menuIdentity = currentMenuIdentity;
            pendingAmounts.clear();
            projectionVersion++;
            sourceVersion = Long.MIN_VALUE;
        }
        long current = CraftingTreeInventorySources.combinedVersion();
        if (current != sourceVersion) {
            sourceSnapshot = CraftingTreeInventorySources.aggregate();
            sourceVersion = current;
            reconcilePendingAmounts();
            rebuildProjection();
        }
        return projectedSnapshot;
    }

    public static synchronized long version() {
        get();
        return 31L * sourceVersion + projectionVersion;
    }

    public static synchronized void applyExpectedChanges(Map<MaterialKey, Long> changes) {
        if (changes == null || changes.isEmpty()) return;
        get();
        for (Map.Entry<MaterialKey, Long> change : changes.entrySet()) {
            MaterialKey key = change.getKey();
            long delta = change.getValue() == null ? 0L : change.getValue();
            if (key == null || delta == 0L) continue;
            PendingAmount pending = pendingAmounts.get(key);
            long baseline = pending == null ? sourceSnapshot.amount(key) : pending.baseline();
            long currentTarget = pending == null ? projectedSnapshot.amount(key) : pending.target();
            long target = delta > 0L
                    ? RecipePlanSolver.saturatedAdd(currentTarget, delta)
                    : Math.max(0L, currentTarget + Math.max(delta, -currentTarget));
            if (target == baseline) {
                pendingAmounts.remove(key);
            } else {
                pendingAmounts.put(key, new PendingAmount(baseline, target));
            }
        }
        projectionVersion++;
        rebuildProjection();
    }

    public static synchronized void invalidate() {
        sourceVersion = Long.MIN_VALUE;
        sourceSnapshot = InventorySnapshot.EMPTY;
        pendingAmounts.clear();
        projectedSnapshot = InventorySnapshot.EMPTY;
        projectionVersion++;
    }

    public static synchronized void clearExpectedChanges() {
        if (pendingAmounts.isEmpty()) return;
        pendingAmounts.clear();
        projectionVersion++;
        rebuildProjection();
    }

    private static void reconcilePendingAmounts() {
        pendingAmounts.entrySet().removeIf(entry -> {
            long real = sourceSnapshot.amount(entry.getKey());
            PendingAmount pending = entry.getValue();
            if (pending.target() > pending.baseline()) {
                return real < pending.baseline() || real >= pending.target();
            }
            return real > pending.baseline() || real <= pending.target();
        });
    }

    private static void rebuildProjection() {
        if (pendingAmounts.isEmpty()) {
            projectedSnapshot = sourceSnapshot;
            return;
        }
        Map<MaterialKey, Long> amounts = new LinkedHashMap<>(sourceSnapshot.amounts());
        for (Map.Entry<MaterialKey, PendingAmount> entry : pendingAmounts.entrySet()) {
            amounts.put(entry.getKey(), entry.getValue().target());
        }
        projectedSnapshot = new InventorySnapshot(amounts);
    }

    private record PendingAmount(long baseline, long target) {
    }

    private static AbstractContainerMenu currentMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.containerMenu;
    }
}