package com.lhy.jeict.compat;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Optional bridge to Just Enough Characters without making it a required dependency. */
public final class JustEnoughCharactersCompat {
    private static final Method CONTAINS = findContainsMethod();
    private static final int MAX_CACHE_ENTRIES = 8192;
    private static final Map<SearchKey, Boolean> PINYIN_CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SearchKey, Boolean> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private JustEnoughCharactersCompat() {
    }

    public static boolean contains(String text, String query) {
        if (text == null || query == null) return false;
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedText.contains(normalizedQuery)) return true;
        if (CONTAINS == null || normalizedQuery.isBlank()) return false;
        SearchKey key = new SearchKey(normalizedText, normalizedQuery);
        synchronized (PINYIN_CACHE) {
            Boolean cached = PINYIN_CACHE.get(key);
            if (cached != null) return cached;
        }
        boolean matched;
        try {
            matched = Boolean.TRUE.equals(CONTAINS.invoke(null, normalizedText, normalizedQuery, true));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            matched = false;
        }
        synchronized (PINYIN_CACHE) {
            PINYIN_CACHE.put(key, matched);
        }
        return matched;
    }

    private record SearchKey(String text, String query) {
    }

    private static Method findContainsMethod() {
        try {
            Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match", false,
                    JustEnoughCharactersCompat.class.getClassLoader());
            return match.getMethod("contains", CharSequence.class, CharSequence.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
