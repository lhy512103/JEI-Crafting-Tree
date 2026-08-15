package com.lhy.jeict.planning;

/** One primary output or byproduct. Expected amounts may be represented by chance. */
public record PlanOutput(PlanMaterial material, long amount, double chance, boolean primary) {
    public PlanOutput {
        amount = Math.max(1L, amount);
        chance = Math.max(0D, Math.min(1D, chance));
    }

    public long expectedAmount(long crafts) {
        if (chance <= 0D || crafts <= 0L) return 0L;
        double expected = (double) amount * (double) crafts * chance;
        return expected >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) Math.floor(expected));
    }
}
