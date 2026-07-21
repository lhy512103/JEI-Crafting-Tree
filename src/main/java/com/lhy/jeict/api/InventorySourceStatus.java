package com.lhy.jeict.api;

/** Immutable diagnostic state for one registered planning inventory source. */
public record InventorySourceStatus(String id, int priority, boolean available, long version, String error) {
    public InventorySourceStatus {
        id = id == null || id.isBlank() ? "unknown" : id;
        error = error == null ? "" : error;
    }
}
