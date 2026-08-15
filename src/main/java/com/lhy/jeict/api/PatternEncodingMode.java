package com.lhy.jeict.api;

/** The AE2 pattern mode represented by an editable recipe-tree draft. */
public enum PatternEncodingMode {
    PROCESSING,
    CRAFTING,
    SMITHING_TABLE,
    STONECUTTING;

    /** Structured recipe modes are encoded against their recipe id and do not allow free-form slot edits. */
    public boolean isStructured() {
        return this != PROCESSING;
    }

    public int inputLimit() {
        return switch (this) {
            case CRAFTING -> 9;
            case SMITHING_TABLE -> 3;
            case STONECUTTING -> 1;
            case PROCESSING -> PatternEncodingDraft.MAX_PROCESSING_INPUTS;
        };
    }

    public int outputLimit() {
        return this == PROCESSING ? PatternEncodingDraft.MAX_PROCESSING_OUTPUTS : 1;
    }
}
