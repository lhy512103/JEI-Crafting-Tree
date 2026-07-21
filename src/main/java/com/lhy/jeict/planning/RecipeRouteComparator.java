package com.lhy.jeict.planning;

import java.util.List;

/** Deterministic route comparison service used by UI and integrations. */
public final class RecipeRouteComparator {
    private RecipeRouteComparator() {
    }

    public static Comparison compare(List<RecipePlanResult> candidates) {
        if (candidates == null || candidates.isEmpty()) return new Comparison(-1, List.of());
        int best = 0;
        RouteScore bestScore = RouteScore.from(candidates.getFirst());
        for (int i = 1; i < candidates.size(); i++) {
            RouteScore score = RouteScore.from(candidates.get(i));
            if (score.compareTo(bestScore) < 0) {
                best = i;
                bestScore = score;
            }
        }
        return new Comparison(best, candidates.stream().map(RouteScore::from).toList());
    }

    public record Comparison(int recommendedIndex, List<RouteScore> scores) {
        public Comparison { scores = List.copyOf(scores); }
    }
}
