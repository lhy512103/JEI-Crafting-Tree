package com.lhy.jeict.planning;

import java.util.List;

/** One recipe input slot, including alternatives and reusable catalyst semantics. */
public record PlanInput(List<PlanMaterial> alternatives, long amount, boolean consumed, int selectedAlternative) {
    public PlanInput {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        amount = Math.max(1L, amount);
        selectedAlternative = alternatives.isEmpty() ? 0 : Math.max(0, Math.min(alternatives.size() - 1, selectedAlternative));
    }

    public PlanInput(List<PlanMaterial> alternatives, long amount) {
        this(alternatives, amount, true, 0);
    }

    public PlanMaterial selected() {
        if (alternatives.isEmpty()) {
            throw new IllegalStateException("Input has no alternatives");
        }
        return alternatives.get(selectedAlternative);
    }
}
