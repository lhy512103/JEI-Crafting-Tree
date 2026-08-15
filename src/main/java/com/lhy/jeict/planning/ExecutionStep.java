package com.lhy.jeict.planning;

/** Ordered execution/checklist entry produced from an exact selected recipe. */
public record ExecutionStep(int index, String project, String recipeId, String title, String machine,
        long crafts, MaterialKey output, long outputAmount) {
}
