package dev.itemguard.item;

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
    void clampsConfiguredValues() {
        ItemValueRegistry registry = new ItemValueRegistry(200, Map.of("ELYTRA", -5));
        assertEquals(100, registry.defaultValue());
        assertEquals(0, registry.valueOf("ELYTRA"));
    }
}
