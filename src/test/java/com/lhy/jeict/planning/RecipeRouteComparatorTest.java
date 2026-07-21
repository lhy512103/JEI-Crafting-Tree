package com.lhy.jeict.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RecipeRouteComparatorTest {
    @Test
    void recommendsLowestRawDemandBeforeOtherCosts() {
        MaterialKey raw = new MaterialKey("item", "test:raw");
        RecipePlanResult expensive = result(Map.of(raw, 10L), Map.of("machine", 1L), 0L);
        RecipePlanResult efficient = result(Map.of(raw, 5L), Map.of("machine", 20L), 30L);

        RecipeRouteComparator.Comparison comparison = RecipeRouteComparator.compare(List.of(expensive, efficient));

        assertEquals(1, comparison.recommendedIndex());
        assertEquals(2, comparison.scores().size());
    }

    @Test
    void usesMachineRunsWasteAndStepsAsDeterministicTieBreakers() {
        MaterialKey raw = new MaterialKey("item", "test:raw");
        RecipePlanResult manyRuns = result(Map.of(raw, 5L), Map.of("machine", 4L), 0L);
        RecipePlanResult fewerRuns = result(Map.of(raw, 5L), Map.of("machine", 2L), 100L);
        assertEquals(1, RecipeRouteComparator.compare(List.of(manyRuns, fewerRuns)).recommendedIndex());
    }

    private static RecipePlanResult result(Map<MaterialKey, Long> raw, Map<String, Long> machines, long waste) {
        Map<MaterialKey, Long> surplus = waste == 0L ? Map.of()
                : Map.of(new MaterialKey("item", "test:waste"), waste);
        return new RecipePlanResult(raw, Map.of(), surplus, Map.of(), machines, List.of(), Set.of());
    }
}
