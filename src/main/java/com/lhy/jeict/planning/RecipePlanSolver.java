package com.lhy.jeict.planning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Global long-amount recipe planner.
 *
 * <p>All targets share one supply ledger. Recipe overproduction and secondary outputs are credited globally,
 * reusable inputs are checked without being consumed, and late byproducts offset earlier raw shortages.
 */
public final class RecipePlanSolver {
    private final SubstitutionStrategy substitutionStrategy;
    private final String preferredNamespace;

    public RecipePlanSolver(SubstitutionStrategy substitutionStrategy, String preferredNamespace) {
        this.substitutionStrategy = substitutionStrategy == null ? SubstitutionStrategy.LOCKED : substitutionStrategy;
        this.preferredNamespace = preferredNamespace == null ? "" : preferredNamespace;
    }

    public RecipePlanSolver() {
        this(SubstitutionStrategy.LOCKED, "");
    }

    public RecipePlanResult solve(List<PlanTarget> targets, InventorySnapshot inventory) {
        State state = new State(inventory == null ? InventorySnapshot.EMPTY : inventory);
        for (PlanTarget target : targets == null ? List.<PlanTarget>of() : targets) {
            checkInterrupted();
            satisfy(target.project(), target.output(), target.amount(), target.root(), state, new HashSet<>());
        }
        List<ExecutionStep> steps = new ArrayList<>(state.steps);
        for (int index = 0; index < steps.size(); index++) {
            ExecutionStep step = steps.get(index);
            steps.set(index, new ExecutionStep(index + 1, step.project(), step.recipeId(), step.title(), step.machine(),
                    step.crafts(), step.output(), step.outputAmount()));
        }
        return new RecipePlanResult(positive(state.raw), positive(state.inventoryUsed), positive(state.supply),
                positiveStrings(state.recipeCrafts), positiveStrings(state.machineRuns), steps, state.cycles);
    }

    private void satisfy(String project, MaterialKey requestedKey, long requestedAmount, PlanNode producer, State state,
            Set<String> path) {
        checkInterrupted();
        long remaining = consumeSupply(requestedKey, requestedAmount, state);
        remaining = consumeInventory(requestedKey, remaining, state);
        if (remaining <= 0L) return;
        if (producer == null) {
            addRaw(requestedKey, remaining, state);
            return;
        }

        PlanRecipe recipe = producer.recipe();
        String cycleKey = recipe.id() + "->" + requestedKey.encoded();
        if (!path.add(cycleKey)) {
            state.cycles.add(cycleKey);
            addRaw(requestedKey, remaining, state);
            return;
        }
        PlanOutput requestedOutput;
        try {
            requestedOutput = recipe.outputFor(requestedKey);
        } catch (IllegalArgumentException ex) {
            addRaw(requestedKey, remaining, state);
            path.remove(cycleKey);
            return;
        }
        long perCraft = Math.max(1L, requestedOutput.expectedAmount(1L));
        long crafts = ceilDiv(remaining, perCraft);
        state.recipeCrafts.merge(recipe.id(), crafts, RecipePlanSolver::saturatedAdd);
        state.machineRuns.merge(recipe.machine(), crafts, RecipePlanSolver::saturatedAdd);

        // Inputs must be allocated before this run's outputs become available. Keep a bounded offset
        // allowance so outputs may cover shortages from earlier independent branches, but cannot erase
        // the initial stock needed to start this very machine run.
        Map<MaterialKey, Long> rawOffsetAllowance = new HashMap<>(state.raw);
        for (int inputIndex = 0; inputIndex < recipe.inputs().size(); inputIndex++) {
            checkInterrupted();
            PlanInput input = recipe.inputs().get(inputIndex);
            if (input.alternatives().isEmpty()) continue;
            long needed = input.consumed() ? saturatedMultiply(input.amount(), crafts) : input.amount();
            allocateInput(project, inputIndex, input, needed, producer, state, path);
        }

        for (PlanOutput output : recipe.outputs()) {
            checkInterrupted();
            addSupply(output.material().key(), output.expectedAmount(crafts), state, rawOffsetAllowance);
        }
        long afterCraft = consumeSupply(requestedKey, remaining, state);
        if (afterCraft > 0L) addRaw(requestedKey, afterCraft, state);
        state.steps.add(new ExecutionStep(0, project, recipe.id(), recipe.title(), recipe.machine(), crafts,
                requestedKey, saturatedMultiply(perCraft, crafts)));
        path.remove(cycleKey);
    }

    private void allocateInput(String project, int inputIndex, PlanInput input, long needed, PlanNode owner,
            State state, Set<String> path) {
        List<PlanMaterial> ordered = orderAlternatives(input, state.inventory);
        if (substitutionStrategy == SubstitutionStrategy.MIX_AVAILABLE) {
            long remaining = needed;
            for (PlanMaterial alternative : ordered) {
                checkInterrupted();
                long before = remaining;
                remaining = consumeSupply(alternative.key(), remaining, state);
                remaining = consumeInventory(alternative.key(), remaining, state);
                if (remaining <= 0L) return;
                if (before == remaining) continue;
            }
            PlanMaterial fallback = ordered.get(0);
            satisfy(project, fallback.key(), remaining, owner.producerFor(inputIndex, fallback.key()), state, path);
            return;
        }
        PlanMaterial selected = ordered.get(0);
        satisfy(project, selected.key(), needed, owner.producerFor(inputIndex, selected.key()), state, path);
    }

    private List<PlanMaterial> orderAlternatives(PlanInput input, InventorySnapshot inventory) {
        List<PlanMaterial> alternatives = new ArrayList<>(input.alternatives());
        PlanMaterial selected = input.selected();
        switch (substitutionStrategy) {
            case MOST_AVAILABLE -> alternatives.sort(Comparator
                    .comparingLong((PlanMaterial material) -> inventory.amount(material.key())).reversed()
                    .thenComparing(material -> material.key().encoded()));
            case PREFERRED_NAMESPACE -> alternatives.sort(Comparator
                    .comparing((PlanMaterial material) -> !preferredNamespace.equals(material.namespace()))
                    .thenComparing(material -> !material.equals(selected))
                    .thenComparing(material -> material.key().encoded()));
            case LOCKED, STRICT_COMPONENTS -> {
                alternatives.remove(selected);
                alternatives.add(0, selected);
            }
            case MIX_AVAILABLE -> alternatives.sort(Comparator
                    .comparingLong((PlanMaterial material) -> inventory.amount(material.key())).reversed()
                    .thenComparing(material -> material.key().encoded()));
        }
        return alternatives;
    }

    private static long consumeSupply(MaterialKey key, long amount, State state) {
        if (amount <= 0L) return 0L;
        long available = state.supply.getOrDefault(key, 0L);
        long used = Math.min(available, amount);
        if (used > 0L) state.supply.put(key, available - used);
        return amount - used;
    }

    private static long consumeInventory(MaterialKey key, long amount, State state) {
        if (amount <= 0L) return 0L;
        long alreadyUsed = state.inventoryUsed.getOrDefault(key, 0L);
        long available = Math.max(0L, state.inventory.amount(key) - alreadyUsed);
        long used = Math.min(available, amount);
        if (used > 0L) state.inventoryUsed.merge(key, used, RecipePlanSolver::saturatedAdd);
        return amount - used;
    }

    private static void addSupply(MaterialKey key, long amount, State state,
            Map<MaterialKey, Long> rawOffsetAllowance) {
        if (amount <= 0L) return;
        long raw = state.raw.getOrDefault(key, 0L);
        long allowance = rawOffsetAllowance.getOrDefault(key, 0L);
        long offset = Math.min(Math.min(raw, allowance), amount);
        if (offset > 0L) {
            state.raw.put(key, raw - offset);
            rawOffsetAllowance.put(key, allowance - offset);
        }
        long remaining = amount - offset;
        if (remaining > 0L) state.supply.merge(key, remaining, RecipePlanSolver::saturatedAdd);
    }

    private static void addRaw(MaterialKey key, long amount, State state) {
        if (amount > 0L) state.raw.merge(key, amount, RecipePlanSolver::saturatedAdd);
    }


    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Recipe planning was superseded by a newer request");
        }
    }

    static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0L) return 0L;
        if (denominator <= 0L) return numerator;
        return 1L + (numerator - 1L) / denominator;
    }

    public static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    public static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static Map<MaterialKey, Long> positive(Map<MaterialKey, Long> source) {
        Map<MaterialKey, Long> result = new LinkedHashMap<>();
        source.entrySet().stream().filter(entry -> entry.getValue() > 0L).sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Map<String, Long> positiveStrings(Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.entrySet().stream().filter(entry -> entry.getValue() > 0L).sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static final class State {
        private final InventorySnapshot inventory;
        private final Map<MaterialKey, Long> supply = new HashMap<>();
        private final Map<MaterialKey, Long> raw = new HashMap<>();
        private final Map<MaterialKey, Long> inventoryUsed = new HashMap<>();
        private final Map<String, Long> recipeCrafts = new HashMap<>();
        private final Map<String, Long> machineRuns = new HashMap<>();
        private final List<ExecutionStep> steps = new ArrayList<>();
        private final Set<String> cycles = new HashSet<>();

        private State(InventorySnapshot inventory) {
            this.inventory = inventory;
        }
    }
}
