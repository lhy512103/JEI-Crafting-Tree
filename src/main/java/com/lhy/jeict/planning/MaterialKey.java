package com.lhy.jeict.planning;

import java.util.Objects;

/** Stable, loader-agnostic identity for an item, fluid, chemical, or custom JEI ingredient. */
public record MaterialKey(String type, String uid) implements Comparable<MaterialKey> {
    public MaterialKey {
        type = normalize(type, "unknown");
        uid = normalize(uid, "missing");
    }

    public static MaterialKey of(String encoded) {
        int split = encoded == null ? -1 : encoded.indexOf('#');
        return split < 0 ? new MaterialKey("unknown", Objects.toString(encoded, "missing"))
                : new MaterialKey(encoded.substring(0, split), encoded.substring(split + 1));
    }

    public String encoded() {
        return type + "#" + uid;
    }

    @Override
    public int compareTo(MaterialKey other) {
        return encoded().compareTo(other.encoded());
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
