package com.lhy.jeict.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;

/** Thread-safe registry and aggregator for built-in and external inventory sources. */
public final class CraftingTreeInventorySources {
    private static final CopyOnWriteArrayList<InventorySource> SOURCES = new CopyOnWriteArrayList<>();

    private CraftingTreeInventorySources() {
    }

    public static void register(InventorySource source) {
        if (source == null) return;
        unregister(safeId(source));
        SOURCES.add(source);
        SOURCES.sort(Comparator.comparingInt(CraftingTreeInventorySources::safePriority).reversed()
                .thenComparing(CraftingTreeInventorySources::safeId));
    }

    public static void unregister(String id) {
        if (id != null) SOURCES.removeIf(source -> id.equals(safeId(source)));
    }

    public static List<InventorySource> sources() {
        return List.copyOf(SOURCES);
    }

    public static InventorySnapshot aggregate() {
        Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
        for (InventorySource source : SOURCES) {
            if (!safeAvailable(source)) continue;
            for (InventoryAmount entry : safeSnapshot(source)) {
                if (entry != null && entry.material() != null && entry.amount() > 0L) {
                    amounts.merge(entry.material(), entry.amount(), RecipePlanSolver::saturatedAdd);
                }
            }
        }
        return new InventorySnapshot(amounts);
    }

    public static long combinedVersion() {
        long version = 1L;
        for (InventorySource source : SOURCES) {
            version = 31L * version + safeId(source).hashCode();
            version = 31L * version + safePriority(source);
            try {
                version = 31L * version + source.version();
                version = 31L * version + (source.isAvailable() ? 1L : 0L);
            } catch (RuntimeException ex) {
                version = 31L * version + ex.getClass().getName().hashCode();
            }
        }
        return version;
    }

    public static List<InventorySourceStatus> statuses() {
        List<InventorySourceStatus> statuses = new ArrayList<>(SOURCES.size());
        for (InventorySource source : SOURCES) {
            try {
                statuses.add(new InventorySourceStatus(safeId(source), safePriority(source), source.isAvailable(),
                        source.version(), ""));
            } catch (RuntimeException ex) {
                statuses.add(new InventorySourceStatus(safeId(source), safePriority(source), false, 0L,
                        ex.getClass().getSimpleName()));
            }
        }
        return List.copyOf(statuses);
    }

    private static String safeId(InventorySource source) {
        try {
            String id = source.id();
            if (id != null && !id.isBlank()) return id;
        } catch (RuntimeException ignored) {
        }
        return source.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(source));
    }

    private static int safePriority(InventorySource source) {
        try {
            return source.priority();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean safeAvailable(InventorySource source) {
        try {
            return source.isAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<InventoryAmount> safeSnapshot(InventorySource source) {
        try {
            List<InventoryAmount> snapshot = source.snapshot();
            return snapshot == null ? List.of() : new ArrayList<>(snapshot);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
