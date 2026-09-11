package dev.itemguard.item;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Risk-weight registry. Missing materials use {@code defaultValue}, never null.
 */
public final class ItemValueRegistry {

    private final int defaultValue;
    private final Map<String, Integer> values;

    public ItemValueRegistry(int defaultValue, Map<String, Integer> values) {
        this.defaultValue = Math.clamp(defaultValue, 0, 100);
        Map<String, Integer> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(normalize(key), Math.clamp(value, 0, 100)));
        this.values = Map.copyOf(copy);
    }

    public int valueOf(String material) {
        if (material == null || material.isBlank() || "AIR".equalsIgnoreCase(material)) {
            return 0;
        }
        return values.getOrDefault(normalize(material), defaultValue);
    }

    public int weighted(String material, int amount) {
        return valueOf(material) * Math.max(0, amount);
    }

    public int defaultValue() {
        return defaultValue;
    }

    public Map<String, Integer> values() {
        return values;
    }

    private static String normalize(String material) {
        return material.trim().toUpperCase(Locale.ROOT);
    }
}
