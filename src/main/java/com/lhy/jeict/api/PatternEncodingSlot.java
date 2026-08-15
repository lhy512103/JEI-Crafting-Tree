package com.lhy.jeict.api;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.ingredients.ITypedIngredient;

/** One editable AE2 pattern slot, independent from AE2 classes. */
public final class PatternEncodingSlot {
    private final List<ITypedIngredient<?>> alternatives;
    private int selectedAlternative;
    private long amount;

    public PatternEncodingSlot(List<ITypedIngredient<?>> alternatives, long amount) {
        this(alternatives, 0, amount);
    }

    public PatternEncodingSlot(List<ITypedIngredient<?>> alternatives, int selectedAlternative, long amount) {
        List<ITypedIngredient<?>> filtered = new ArrayList<>();
        if (alternatives != null) {
            for (ITypedIngredient<?> alternative : alternatives) {
                if (alternative != null) {
                    filtered.add(alternative);
                }
            }
        }
        this.alternatives = List.copyOf(filtered);
        this.selectedAlternative = this.alternatives.isEmpty()
                ? 0
                : Math.floorMod(selectedAlternative, this.alternatives.size());
        this.amount = Math.max(1L, amount);
    }

    public List<ITypedIngredient<?>> alternatives() {
        return alternatives;
    }

    public @Nullable ITypedIngredient<?> ingredient() {
        return alternatives.isEmpty() ? null : alternatives.get(selectedAlternative);
    }

    public int selectedAlternative() {
        return selectedAlternative;
    }

    public long amount() {
        return amount;
    }

    public boolean hasAlternatives() {
        return alternatives.size() > 1;
    }

    public void cycleAlternative(int direction) {
        if (alternatives.size() > 1) {
            selectedAlternative = Math.floorMod(selectedAlternative + direction, alternatives.size());
        }
    }

    /** 树上的替代品切换同步到 draft 时使用；越界索引被钳制。 */
    public void setSelectedAlternative(int index) {
        if (alternatives.size() > 1) {
            selectedAlternative = Math.max(0, Math.min(alternatives.size() - 1, index));
        }
    }

    public void setAmount(long amount) {
        this.amount = Math.max(1L, amount);
    }

    public PatternEncodingSlot copy() {
        return new PatternEncodingSlot(alternatives, selectedAlternative, amount);
    }

    public String fingerprint() {
        ITypedIngredient<?> selected = ingredient();
        String ingredientPart = selected == null
                ? "empty"
                : selected.getType().getUid() + "#" + String.valueOf(selected.getIngredient());
        return ingredientPart + "@" + amount + "/" + selectedAlternative;
    }
}
