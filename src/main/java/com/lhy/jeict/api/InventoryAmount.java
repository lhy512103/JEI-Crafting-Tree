package com.lhy.jeict.api;

import com.lhy.jeict.planning.MaterialKey;

/** One immutable amount supplied by an inventory integration. */
public record InventoryAmount(MaterialKey material, long amount) {
    public InventoryAmount {
        amount = Math.max(0L, amount);
    }
}
