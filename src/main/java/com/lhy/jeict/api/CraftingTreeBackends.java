package com.lhy.jeict.api;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

/**
 * Client-thread registry for optional recipe-tree backends.
 *
 * <p>The active backend is the highest-priority registration. Equal priorities are resolved by id, making the
 * result deterministic. Failures in third-party implementations are isolated by consumers, not swallowed here.
 */
public final class CraftingTreeBackends {
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final String LEGACY_ID = "jeict:legacy";

    private CraftingTreeBackends() {
    }

    /**
     * Legacy replacement registration. New integrations should retain the returned handle from
     * {@link #register(String, int, CraftingTreeBackend)} and unregister it during client shutdown.
     */
    public static void register(CraftingTreeBackend backend) {
        unregister(LEGACY_ID);
        if (backend != null) register(LEGACY_ID, 0, backend);
    }

    public static ApiRegistration register(String id, int priority, CraftingTreeBackend backend) {
        if (id == null || id.isBlank() || backend == null) return ApiRegistration.NOOP;
        Entry entry = new Entry(id, priority, backend);
        unregister(id);
        ENTRIES.add(entry);
        sort();
        return () -> ENTRIES.remove(entry);
    }

    public static void unregister(String id) {
        if (id != null) ENTRIES.removeIf(entry -> id.equals(entry.id));
    }

    /** Returns the selected backend, or {@code null} when no integration is installed. */
    public static @Nullable CraftingTreeBackend get() {
        return ENTRIES.isEmpty() ? null : ENTRIES.get(0).backend;
    }

    public static boolean isPresent() {
        return get() != null;
    }

    /** Immutable diagnostics for all registered backends in selection order. */
    public static List<Registration> registrations() {
        return ENTRIES.stream().map(entry -> new Registration(entry.id, entry.priority, entry.backend == get()))
                .toList();
    }

    public record Registration(String id, int priority, boolean active) {
    }

    private static void sort() {
        ENTRIES.sort(Comparator.comparingInt((Entry entry) -> entry.priority).reversed().thenComparing(entry -> entry.id));
    }

    private record Entry(String id, int priority, CraftingTreeBackend backend) {
    }
}
