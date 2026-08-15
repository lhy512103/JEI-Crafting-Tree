package com.lhy.jeict.planning;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete immutable result of one global multi-project planning pass. */
public record RecipePlanResult(
        Map<MaterialKey, Long> rawRequirements,
        Map<MaterialKey, Long> inventoryUsed,
        Map<MaterialKey, Long> surplus,
        Map<String, Long> recipeCrafts,
        Map<String, Long> machineRuns,
        List<ExecutionStep> executionSteps,
        Set<String> cycleDiagnostics) {
    public RecipePlanResult {
        rawRequirements = Map.copyOf(rawRequirements);
        inventoryUsed = Map.copyOf(inventoryUsed);
        surplus = Map.copyOf(surplus);
        recipeCrafts = Map.copyOf(recipeCrafts);
        machineRuns = Map.copyOf(machineRuns);
        executionSteps = List.copyOf(executionSteps);
        cycleDiagnostics = Set.copyOf(cycleDiagnostics);
    }

    public long totalRawUnits() {
        return rawRequirements.values().stream().reduce(0L, RecipePlanSolver::saturatedAdd);
    }

    public long totalWasteUnits() {
        return surplus.values().stream().reduce(0L, RecipePlanSolver::saturatedAdd);
    }
}
