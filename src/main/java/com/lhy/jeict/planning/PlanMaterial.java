package com.lhy.jeict.planning;

import java.util.Objects;

/** A generic material reference. Amounts live on inputs/outputs so this value can be shared safely. */
public record PlanMaterial(MaterialKey key, String displayName, String namespace) {
    public PlanMaterial {
        Objects.requireNonNull(key, "key");
        displayName = displayName == null || displayName.isBlank() ? key.uid() : displayName;
        namespace = namespace == null ? namespaceOf(key.uid()) : namespace;
    }

    public PlanMaterial(MaterialKey key, String displayName) {
        this(key, displayName, null);
    }

    private static String namespaceOf(String uid) {
        int split = uid.indexOf(':');
        return split > 0 ? uid.substring(0, split) : "";
    }
}
