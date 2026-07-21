package com.lhy.jeict.planning;

import java.util.List;

/** Immutable exact recipe route used by the global planner. */
public record PlanRecipe(String id, String title, String machine, List<PlanInput> inputs, List<PlanOutput> outputs) {
    public PlanRecipe {
        id = id == null || id.isBlank() ? "anonymous:" + Integer.toHexString(System.identityHashCode(outputs)) : id;
        title = title == null || title.isBlank() ? id : title;
        machine = machine == null || machine.isBlank() ? "crafting" : machine;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        if (outputs.isEmpty()) throw new IllegalArgumentException("Recipe must expose at least one output");
    }

    public PlanOutput outputFor(MaterialKey key) {
        return outputs.stream().filter(output -> output.material().key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Recipe " + id + " does not produce " + key));
    }
}
