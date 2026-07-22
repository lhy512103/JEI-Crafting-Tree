package com.lhy.jeict.api;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeOutputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side editable representation of the AE2 pattern that will actually be
 * encoded. It deliberately does not contain any AE2 classes, keeping JEICT's
 * AE2 integration optional.
 */
public final class PatternEncodingDraft {
    public static final int MAX_PROCESSING_INPUTS = 81;
    public static final int MAX_PROCESSING_OUTPUTS = 27;
    public static final int VISIBLE_INPUTS = 9;
    public static final int VISIBLE_OUTPUTS = 3;

    private final String sourceRecipeIdentity;
    private final PatternEncodingMode mode;
    private final @Nullable ResourceLocation recipeId;
    private final String patternName;
    private final List<@Nullable PatternEncodingSlot> inputs;
    private final List<@Nullable PatternEncodingSlot> outputs;
    private boolean substituteItems;
    private boolean substituteFluids;
    private boolean preserveInputOrder = true;
    private final int originalInputCount;
    private final int originalOutputCount;
    private final String originalPrimaryOutputFingerprint;
    private String originalFingerprint;

    private PatternEncodingDraft(String sourceRecipeIdentity, PatternEncodingMode mode,
            @Nullable ResourceLocation recipeId, String patternName,
            List<@Nullable PatternEncodingSlot> inputs, List<@Nullable PatternEncodingSlot> outputs,
            boolean substituteItems, boolean substituteFluids, boolean preserveInputOrder) {
        this.sourceRecipeIdentity = sourceRecipeIdentity;
        this.mode = mode;
        this.recipeId = recipeId;
        this.patternName = patternName == null || patternName.isBlank() ? "-" : patternName;
        this.inputs = new ArrayList<>(inputs);
        this.outputs = new ArrayList<>(outputs);
        this.substituteItems = substituteItems;
        this.substituteFluids = substituteFluids;
        this.preserveInputOrder = preserveInputOrder;
        this.originalInputCount = countPresent(this.inputs);
        this.originalOutputCount = countPresent(this.outputs);
        PatternEncodingSlot primary = this.outputs.isEmpty() ? null : this.outputs.getFirst();
        this.originalPrimaryOutputFingerprint = primary == null ? "" : primary.fingerprint();
        this.originalFingerprint = fingerprint();
    }

    public static PatternEncodingDraft fromRecipe(RecipeTreeRecipeViewModel recipe, PatternEncodingMode mode,
            boolean substituteItems, boolean substituteFluids) {
        List<@Nullable PatternEncodingSlot> inputs = new ArrayList<>();
        int inputLimit = mode == PatternEncodingMode.CRAFTING ? 9 : MAX_PROCESSING_INPUTS;
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            if (inputs.size() >= inputLimit) break;
            List<ITypedIngredient<?>> alternatives = new ArrayList<>();
            for (RecipeTreeInputViewModel.DisplayOption option : input.orderedDisplayOptions()) {
                if (option.typedIngredient() != null) alternatives.add(option.typedIngredient());
            }
            inputs.add(alternatives.isEmpty() ? null : new PatternEncodingSlot(alternatives, input.longAmount()));
        }

        List<RecipeTreeOutputViewModel> orderedOutputs = new ArrayList<>(recipe.outputs());
        orderedOutputs.sort((left, right) -> Boolean.compare(right.primary(), left.primary()));
        List<@Nullable PatternEncodingSlot> outputs = new ArrayList<>();
        int outputLimit = mode == PatternEncodingMode.CRAFTING ? 1 : MAX_PROCESSING_OUTPUTS;
        for (RecipeTreeOutputViewModel output : orderedOutputs) {
            if (outputs.size() >= outputLimit) break;
            outputs.add(output.ingredient() == null ? null
                    : new PatternEncodingSlot(List.of(output.ingredient()), output.amount()));
        }
        if (outputs.isEmpty() && recipe.primaryOutputIngredient() != null) {
            outputs.add(new PatternEncodingSlot(List.of(recipe.primaryOutputIngredient()), recipe.primaryOutputAmount()));
        }
        return new PatternEncodingDraft(recipe.stableIdentity(), mode, recipe.recipeId(), recipe.title().getString(),
                inputs, outputs, substituteItems, substituteFluids, true);
    }

    public String sourceRecipeIdentity() { return sourceRecipeIdentity; }
    public PatternEncodingMode mode() { return mode; }
    public @Nullable ResourceLocation recipeId() { return recipeId; }
    public String patternName() { return patternName; }
    public List<@Nullable PatternEncodingSlot> inputs() { return inputs; }
    public List<@Nullable PatternEncodingSlot> outputs() { return outputs; }
    public boolean substituteItems() { return substituteItems; }
    public boolean substituteFluids() { return substituteFluids; }
    public boolean preserveInputOrder() { return preserveInputOrder; }

    public void setSubstituteItems(boolean value) { substituteItems = value; }
    public void setSubstituteFluids(boolean value) { substituteFluids = value; }
    public void setPreserveInputOrder(boolean value) { preserveInputOrder = value; }

    public @Nullable PatternEncodingSlot input(int index) {
        return index >= 0 && index < inputs.size() ? inputs.get(index) : null;
    }

    public @Nullable PatternEncodingSlot output(int index) {
        return index >= 0 && index < outputs.size() ? outputs.get(index) : null;
    }

    public void setInput(int index, @Nullable PatternEncodingSlot slot) {
        setSlot(inputs, index, slot, mode == PatternEncodingMode.CRAFTING ? 9 : MAX_PROCESSING_INPUTS);
    }

    public void setOutput(int index, @Nullable PatternEncodingSlot slot) {
        setSlot(outputs, index, slot, mode == PatternEncodingMode.CRAFTING ? 1 : MAX_PROCESSING_OUTPUTS);
    }

    private static void setSlot(List<@Nullable PatternEncodingSlot> slots, int index,
            @Nullable PatternEncodingSlot slot, int limit) {
        if (index < 0 || index >= limit) return;
        while (slots.size() <= index) slots.add(null);
        slots.set(index, slot);
        trimTrailingEmpty(slots);
    }

    private static void trimTrailingEmpty(List<@Nullable PatternEncodingSlot> slots) {
        while (!slots.isEmpty() && slots.getLast() == null) slots.removeLast();
    }

    public void clear() {
        inputs.clear();
        outputs.clear();
    }

    public void cyclePrimaryOutput() {
        if (mode != PatternEncodingMode.PROCESSING || outputs.size() < 2) return;
        PatternEncodingSlot first = outputs.removeFirst();
        outputs.add(first);
    }

    public void promoteOutput(int index) {
        if (mode != PatternEncodingMode.PROCESSING || index <= 0 || index >= outputs.size()) return;
        PatternEncodingSlot selected = outputs.remove(index);
        outputs.addFirst(selected);
    }

    public boolean isDirty() {
        return !originalFingerprint.equals(fingerprint());
    }

    public boolean hasRemovedSourceInput() {
        return countPresent(inputs) < originalInputCount;
    }

    public boolean hasRemovedSourceOutput() {
        return countPresent(outputs) < originalOutputCount;
    }

    public boolean primaryOutputChanged() {
        PatternEncodingSlot primary = outputs.isEmpty() ? null : outputs.getFirst();
        return !originalPrimaryOutputFingerprint.equals(primary == null ? "" : primary.fingerprint());
    }

    private static int countPresent(List<@Nullable PatternEncodingSlot> slots) {
        int count = 0;
        for (PatternEncodingSlot slot : slots) if (slot != null && slot.ingredient() != null) count++;
        return count;
    }

    public String fingerprint() {
        StringBuilder result = new StringBuilder(mode.name()).append('|')
                .append(recipeId).append('|').append(substituteItems).append('|')
                .append(substituteFluids).append('|').append(preserveInputOrder).append("|I:");
        appendSlots(result, inputs);
        result.append("|O:");
        appendSlots(result, outputs);
        return result.toString();
    }

    private static void appendSlots(StringBuilder result, List<@Nullable PatternEncodingSlot> slots) {
        for (PatternEncodingSlot slot : slots) {
            result.append(slot == null ? "_" : slot.fingerprint()).append(';');
        }
    }
}
