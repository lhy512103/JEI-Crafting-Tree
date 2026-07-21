package com.lhy.jeict.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RecipePlanSolverTest {
    private static PlanMaterial material(String id) {
        return new PlanMaterial(new MaterialKey("item", id), id);
    }

    @Test
    void sharesOverproductionAcrossProjects() {
        PlanMaterial log = material("minecraft:oak_log");
        PlanMaterial plank = material("minecraft:oak_planks");
        PlanMaterial targetA = material("test:a");
        PlanMaterial targetB = material("test:b");

        PlanRecipe plankRecipe = new PlanRecipe("planks", "Planks", "crafting",
                List.of(new PlanInput(List.of(log), 1)),
                List.of(new PlanOutput(plank, 4, 1, true)));
        PlanNode plankNode = new PlanNode(plankRecipe);
        PlanNode aNode = new PlanNode(new PlanRecipe("a", "A", "crafting",
                List.of(new PlanInput(List.of(plank), 1)), List.of(new PlanOutput(targetA, 1, 1, true))),
                Map.of(plank.key(), plankNode));
        PlanNode bNode = new PlanNode(new PlanRecipe("b", "B", "crafting",
                List.of(new PlanInput(List.of(plank), 1)), List.of(new PlanOutput(targetB, 1, 1, true))),
                Map.of(plank.key(), plankNode));

        RecipePlanResult result = new RecipePlanSolver().solve(List.of(
                new PlanTarget("one", aNode, targetA.key(), 1),
                new PlanTarget("two", bNode, targetB.key(), 1)), InventorySnapshot.EMPTY);

        assertEquals(1L, result.rawRequirements().get(log.key()));
        assertEquals(1L, result.recipeCrafts().get("planks"));
        assertEquals(2L, result.surplus().get(plank.key()));
    }

    @Test
    void secondaryOutputOffsetsEarlierRawDemand() {
        PlanMaterial ore = material("test:ore");
        PlanMaterial slag = material("test:slag");
        PlanMaterial first = material("test:first");
        PlanMaterial second = material("test:second");

        PlanNode firstNode = new PlanNode(new PlanRecipe("first", "First", "machine_a",
                List.of(new PlanInput(List.of(slag), 2)), List.of(new PlanOutput(first, 1, 1, true))));
        PlanNode secondNode = new PlanNode(new PlanRecipe("second", "Second", "machine_b",
                List.of(new PlanInput(List.of(ore), 1)), List.of(
                        new PlanOutput(second, 1, 1, true),
                        new PlanOutput(slag, 2, 1, false))));

        RecipePlanResult result = new RecipePlanSolver().solve(List.of(
                new PlanTarget("early", firstNode, first.key(), 1),
                new PlanTarget("late", secondNode, second.key(), 1)), InventorySnapshot.EMPTY);

        assertTrue(!result.rawRequirements().containsKey(slag.key()));
        assertEquals(1L, result.rawRequirements().get(ore.key()));
    }

    @Test
    void doesNotUseSameRunByproductAsItsOwnConsumedInput() {
        PlanMaterial seed = material("test:seed");
        PlanMaterial output = material("test:grown_output");
        PlanNode node = new PlanNode(new PlanRecipe("grow", "Grow", "machine",
                List.of(new PlanInput(List.of(seed), 1)),
                List.of(new PlanOutput(output, 1, 1, true), new PlanOutput(seed, 1, 1, false))));

        RecipePlanResult result = new RecipePlanSolver().solve(
                List.of(new PlanTarget("grow", node, output.key(), 1)), InventorySnapshot.EMPTY);

        assertEquals(1L, result.rawRequirements().get(seed.key()));
        assertEquals(1L, result.surplus().get(seed.key()));
    }

    @Test
    void reusableCatalystIsNotMultipliedByCraftCount() {
        PlanMaterial catalyst = material("test:catalyst");
        PlanMaterial dust = material("test:dust");
        PlanMaterial output = material("test:output");
        PlanNode node = new PlanNode(new PlanRecipe("catalytic", "Catalytic", "reactor",
                List.of(new PlanInput(List.of(catalyst), 1, false, 0), new PlanInput(List.of(dust), 2)),
                List.of(new PlanOutput(output, 1, 1, true))));

        RecipePlanResult result = new RecipePlanSolver().solve(
                List.of(new PlanTarget("batch", node, output.key(), 5)), InventorySnapshot.EMPTY);

        assertEquals(1L, result.rawRequirements().get(catalyst.key()));
        assertEquals(10L, result.rawRequirements().get(dust.key()));
        assertEquals(5L, result.machineRuns().get("reactor"));
    }

    @Test
    void mixesAlternativesFromInventory() {
        PlanMaterial copperA = material("a:copper");
        PlanMaterial copperB = material("b:copper");
        PlanMaterial output = material("test:wire");
        PlanNode node = new PlanNode(new PlanRecipe("wire", "Wire", "press",
                List.of(new PlanInput(List.of(copperA, copperB), 5)),
                List.of(new PlanOutput(output, 1, 1, true))));
        InventorySnapshot inventory = new InventorySnapshot(Map.of(copperA.key(), 2L, copperB.key(), 3L));

        RecipePlanResult result = new RecipePlanSolver(SubstitutionStrategy.MIX_AVAILABLE, "")
                .solve(List.of(new PlanTarget("wire", node, output.key(), 1)), inventory);

        assertTrue(result.rawRequirements().isEmpty());
        assertEquals(2L, result.inventoryUsed().get(copperA.key()));
        assertEquals(3L, result.inventoryUsed().get(copperB.key()));
    }


    @Test
    void preferredNamespaceWinsEvenWhenAnotherAlternativeHasMoreStock() {
        PlanMaterial preferred = material("preferred:copper");
        PlanMaterial other = material("other:copper");
        PlanMaterial output = material("test:plate");
        PlanNode node = new PlanNode(new PlanRecipe("plate", "Plate", "press",
                List.of(new PlanInput(List.of(other, preferred), 2)),
                List.of(new PlanOutput(output, 1, 1, true))));
        InventorySnapshot inventory = new InventorySnapshot(Map.of(other.key(), 64L, preferred.key(), 2L));

        RecipePlanResult result = new RecipePlanSolver(SubstitutionStrategy.PREFERRED_NAMESPACE, "preferred")
                .solve(List.of(new PlanTarget("plate", node, output.key(), 1)), inventory);

        assertEquals(2L, result.inventoryUsed().get(preferred.key()));
        assertFalse(result.inventoryUsed().containsKey(other.key()));
    }

    @Test
    void lockedAndStrictStrategiesHonorSelectedAlternative() {
        PlanMaterial first = material("test:first");
        PlanMaterial selected = material("test:selected");
        PlanMaterial output = material("test:result");
        PlanInput input = new PlanInput(List.of(first, selected), 3L, true, 1);
        PlanNode node = new PlanNode(new PlanRecipe("locked", "Locked", "crafting", List.of(input),
                List.of(new PlanOutput(output, 1, 1, true))));

        for (SubstitutionStrategy strategy : List.of(SubstitutionStrategy.LOCKED,
                SubstitutionStrategy.STRICT_COMPONENTS)) {
            RecipePlanResult result = new RecipePlanSolver(strategy, "")
                    .solve(List.of(new PlanTarget("locked", node, output.key(), 1)), InventorySnapshot.EMPTY);
            assertEquals(3L, result.rawRequirements().get(selected.key()));
            assertFalse(result.rawRequirements().containsKey(first.key()));
        }
    }

    @Test
    void preservesDifferentSelectedProducersForIdenticalInputMaterials() {
        PlanMaterial shared = material("test:shared");
        PlanMaterial rawA = material("test:raw_a");
        PlanMaterial rawB = material("test:raw_b");
        PlanMaterial output = material("test:combined");
        PlanNode producerA = new PlanNode(new PlanRecipe("producer_a", "Producer A", "machine_a",
                List.of(new PlanInput(List.of(rawA), 1)), List.of(new PlanOutput(shared, 1, 1, true))));
        PlanNode producerB = new PlanNode(new PlanRecipe("producer_b", "Producer B", "machine_b",
                List.of(new PlanInput(List.of(rawB), 1)), List.of(new PlanOutput(shared, 1, 1, true))));
        PlanRecipe combined = new PlanRecipe("combined", "Combined", "assembler",
                List.of(new PlanInput(List.of(shared), 1), new PlanInput(List.of(shared), 1)),
                List.of(new PlanOutput(output, 1, 1, true)));
        PlanNode root = new PlanNode(combined, Map.of(shared.key(), producerA), Map.of(0, producerA, 1, producerB));

        RecipePlanResult result = new RecipePlanSolver().solve(
                List.of(new PlanTarget("combined", root, output.key(), 1)), InventorySnapshot.EMPTY);

        assertEquals(1L, result.rawRequirements().get(rawA.key()));
        assertEquals(1L, result.rawRequirements().get(rawB.key()));
        assertEquals(1L, result.recipeCrafts().get("producer_a"));
        assertEquals(1L, result.recipeCrafts().get("producer_b"));
    }

    @Test
    void reportsRecursiveRecipeCyclesWithoutOverflowingTheStack() {
        PlanMaterial loop = material("test:loop");
        PlanRecipe recipe = new PlanRecipe("loop_recipe", "Loop", "crafting",
                List.of(new PlanInput(List.of(loop), 1)), List.of(new PlanOutput(loop, 1, 1, true)));
        PlanNode inner = new PlanNode(recipe);
        PlanNode outer = new PlanNode(recipe, Map.of(loop.key(), inner));

        RecipePlanResult result = new RecipePlanSolver()
                .solve(List.of(new PlanTarget("loop", outer, loop.key(), 1)), InventorySnapshot.EMPTY);

        assertTrue(result.cycleDiagnostics().contains("loop_recipe->item#test:loop"));
        assertEquals(1L, result.rawRequirements().get(loop.key()));
    }

    @Test
    void saturatesLongArithmeticAndLargePlans() {
        assertEquals(Long.MAX_VALUE, RecipePlanSolver.saturatedAdd(Long.MAX_VALUE - 2L, 10L));
        assertEquals(Long.MAX_VALUE, RecipePlanSolver.saturatedMultiply(Long.MAX_VALUE, 2L));

        PlanMaterial raw = material("test:raw");
        PlanMaterial output = material("test:huge");
        PlanNode node = new PlanNode(new PlanRecipe("huge", "Huge", "machine",
                List.of(new PlanInput(List.of(raw), Long.MAX_VALUE)),
                List.of(new PlanOutput(output, 1, 1, true))));
        RecipePlanResult result = new RecipePlanSolver().solve(
                List.of(new PlanTarget("huge", node, output.key(), Long.MAX_VALUE)), InventorySnapshot.EMPTY);
        assertEquals(Long.MAX_VALUE, result.rawRequirements().get(raw.key()));
        assertEquals(Long.MAX_VALUE, result.machineRuns().get("machine"));
    }

    @Test
    void catalystCanBeSatisfiedFromInventoryWithoutConsumptionPerCraft() {
        PlanMaterial catalyst = material("test:catalyst_in_stock");
        PlanMaterial output = material("test:inventory_catalyst_result");
        PlanNode node = new PlanNode(new PlanRecipe("inventory_catalyst", "Catalyst", "reactor",
                List.of(new PlanInput(List.of(catalyst), 1L, false, 0)),
                List.of(new PlanOutput(output, 1, 1, true))));
        RecipePlanResult result = new RecipePlanSolver().solve(
                List.of(new PlanTarget("batch", node, output.key(), 500L)),
                new InventorySnapshot(Map.of(catalyst.key(), 1L)));

        assertTrue(result.rawRequirements().isEmpty());
        assertEquals(1L, result.inventoryUsed().get(catalyst.key()));
    }
}
