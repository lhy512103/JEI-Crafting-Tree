package com.lhy.jeict.planning;

/** One requested project target. Multiple targets share one global material ledger. */
public record PlanTarget(String project, PlanNode root, MaterialKey output, long amount) {
    public PlanTarget {
        project = project == null || project.isBlank() ? "default" : project;
        amount = Math.max(1L, amount);
    }
}
