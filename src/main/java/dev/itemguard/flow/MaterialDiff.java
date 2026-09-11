package dev.itemguard.flow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fast material-count comparison used by reconciliation. Signature hashing is a second pass.
 */
public final class MaterialDiff {

    private MaterialDiff() {
    }

    public static Map<String, Integer> subtract(Map<String, Integer> after, Map<String, Integer> before) {
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(before, "before");
        Map<String, Integer> delta = new LinkedHashMap<>();
        for (String key : unionKeys(before, after)) {
            int change = after.getOrDefault(key, 0) - before.getOrDefault(key, 0);
            if (change != 0 && !"AIR".equals(key)) {
                delta.put(key, change);
            }
        }
        return Map.copyOf(delta);
    }

    public static Map<String, Integer> positive(Map<String, Integer> delta) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        delta.forEach((material, change) -> {
            if (change > 0) {
                gains.put(material, change);
            }
        });
        return Map.copyOf(gains);
    }

    public static Map<String, Integer> negative(Map<String, Integer> delta) {
        Map<String, Integer> losses = new LinkedHashMap<>();
        delta.forEach((material, change) -> {
            if (change < 0) {
                losses.put(material, -change);
            }
        });
        return Map.copyOf(losses);
    }

    private static Iterable<String> unionKeys(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> keys = new LinkedHashMap<>(left);
        right.forEach(keys::putIfAbsent);
        return keys.keySet();
    }
}
