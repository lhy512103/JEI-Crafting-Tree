package com.lhy.jeict.planning;

import java.util.List;
import java.util.Map;

/** A selected recipe node with exact per-input child producers. */
public record PlanNode(PlanRecipe recipe, Map<MaterialKey, PlanNode> producers,
        Map<Integer, PlanNode> inputProducers) {
    public PlanNode {
        producers = producers == null ? Map.of() : Map.copyOf(producers);
        inputProducers = inputProducers == null ? Map.of() : Map.copyOf(inputProducers);
    }

    public PlanNode(PlanRecipe recipe, Map<MaterialKey, PlanNode> producers) {
        this(recipe, producers, Map.of());
    }

    public PlanNode(PlanRecipe recipe) {
        this(recipe, Map.of(), Map.of());
    }

    public PlanNode producerFor(int inputIndex, MaterialKey material) {
        PlanNode exact = inputProducers.get(inputIndex);
        return exact == null ? producers.get(material) : exact;
    }

    public PlanNode producerFor(List<PlanMaterial> alternatives) {
        for (PlanMaterial alternative : alternatives) {
            PlanNode producer = producers.get(alternative.key());
            if (producer != null) return producer;
        }
        return null;
    }
}
