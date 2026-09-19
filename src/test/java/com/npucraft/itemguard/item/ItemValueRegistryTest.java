package com.npucraft.itemguard.item;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemValueRegistryTest {

    @Test
    void missingMaterialUsesDefault() {
        ItemValueRegistry registry = new ItemValueRegistry(1, Map.of("DIAMOND", 25, "NETHERITE_BLOCK", 80));
        assertEquals(25, registry.valueOf("diamond"));
        assertEquals(80, registry.valueOf("NETHERITE_BLOCK"));
        assertEquals(1, registry.valueOf("DIRT"));
        assertEquals(0, registry.valueOf("AIR"));
        assertEquals(160, registry.weighted("NETHERITE_BLOCK", 2));
    }

    @Test
    void operatorEquipmentOverrideWinsOverSuggestedFallback() {
        ItemValueRegistry merged = new ItemValueRegistry(1, ItemValueDefaults.mergeOperatorValues(Map.of(
                "DIAMOND", 25,
                "DIAMOND_CHESTPLATE", 1
        )));
        assertEquals(24, new ItemValueRegistry(1, ItemValueDefaults.mergeOperatorValues(Map.of("DIAMOND", 25))).valueOf("DIAMOND_CHESTPLATE"));
        assertEquals(1, merged.valueOf("DIAMOND_CHESTPLATE"));
        assertEquals(25, merged.valueOf("DIAMOND"));
        assertEquals(1, merged.valueOf("DIRT"));
        assertEquals(55, new ItemValueRegistry(1, ItemValueDefaults.mergeOperatorValues(Map.of())).valueOf("NETHERITE_CHESTPLATE"));
        assertEquals(40, new ItemValueRegistry(1, ItemValueDefaults.mergeOperatorValues(Map.of())).valueOf("NETHERITE_UPGRADE_SMITHING_TEMPLATE"));
    }

    @Test
    void clampsConfiguredValues() {
        ItemValueRegistry registry = new ItemValueRegistry(200, Map.of("ELYTRA", -5));
        assertEquals(100, registry.defaultValue());
        assertEquals(0, registry.valueOf("ELYTRA"));
    }
}
