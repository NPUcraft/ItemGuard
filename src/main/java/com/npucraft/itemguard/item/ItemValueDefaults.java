package com.npucraft.itemguard.item;

import java.util.HashMap;
import java.util.Map;

/**
 * Bundled gap-fill weights for high-tier equipment missing from older operator {@code items.yml}.
 * Operator keys always win. This is not a price table.
 */
public final class ItemValueDefaults {

    private ItemValueDefaults() {
    }

    public static Map<String, Integer> suggestedEquipmentWeights() {
        return Map.ofEntries(
                Map.entry("DIAMOND_HELMET", 20),
                Map.entry("DIAMOND_CHESTPLATE", 24),
                Map.entry("DIAMOND_LEGGINGS", 23),
                Map.entry("DIAMOND_BOOTS", 18),
                Map.entry("DIAMOND_AXE", 20),
                Map.entry("DIAMOND_SHOVEL", 16),
                Map.entry("DIAMOND_HOE", 12),
                Map.entry("NETHERITE_HELMET", 48),
                Map.entry("NETHERITE_CHESTPLATE", 55),
                Map.entry("NETHERITE_LEGGINGS", 52),
                Map.entry("NETHERITE_BOOTS", 45),
                Map.entry("NETHERITE_AXE", 48),
                Map.entry("NETHERITE_SHOVEL", 42),
                Map.entry("NETHERITE_HOE", 30),
                Map.entry("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 40)
        );
    }

    /**
     * Suggested equipment weights first, then operator file values. Missing operator keys keep the suggestion
     * instead of {@code default-value}. Explicit operator keys, including {@code 1}, are preserved.
     */
    public static Map<String, Integer> mergeOperatorValues(Map<String, Integer> operatorValues) {
        Map<String, Integer> merged = new HashMap<>(suggestedEquipmentWeights());
        if (operatorValues != null) {
            merged.putAll(operatorValues);
        }
        return merged;
    }
}
