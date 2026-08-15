package com.lhy.jeict.planning;

import java.util.List;

/** Human-readable execution/checklist projection of a plan. */
public final class ExecutionChecklist {
    private ExecutionChecklist() {
    }

    public static List<String> lines(RecipePlanResult result) {
        return result.executionSteps().stream().map(step -> String.format(
                "%d. [%s] %s × %d -> %s (%d)", step.index(), step.machine(), step.title(), step.crafts(),
                step.output().encoded(), step.outputAmount())).toList();
    }
}
