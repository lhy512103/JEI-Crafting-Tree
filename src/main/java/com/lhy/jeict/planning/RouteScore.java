package com.lhy.jeict.planning;

/** Comparable score used by route comparison UI. Lower values are better. */
public record RouteScore(long rawUnits, long machineRuns, long distinctMachines, long wasteUnits, long steps)
        implements Comparable<RouteScore> {
    public static RouteScore from(RecipePlanResult result) {
        long runs = result.machineRuns().values().stream().reduce(0L, RecipePlanSolver::saturatedAdd);
        return new RouteScore(result.totalRawUnits(), runs, result.machineRuns().size(), result.totalWasteUnits(),
                result.executionSteps().size());
    }

    @Override
    public int compareTo(RouteScore other) {
        int compared = Long.compare(rawUnits, other.rawUnits);
        if (compared != 0) return compared;
        compared = Long.compare(machineRuns, other.machineRuns);
        if (compared != 0) return compared;
        compared = Long.compare(distinctMachines, other.distinctMachines);
        if (compared != 0) return compared;
        compared = Long.compare(wasteUnits, other.wasteUnits);
        return compared != 0 ? compared : Long.compare(steps, other.steps);
    }
}
