package com.npucraft.itemguard.flow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialDiffTest {

    @Test
    void computesGainsAndLosses() {
        Map<String, Integer> before = Map.of("DIAMOND", 64, "STONE", 16);
        Map<String, Integer> after = Map.of("DIAMOND", 128, "APPLE", 8);
        Map<String, Integer> delta = MaterialDiff.subtract(after, before);
        assertEquals(64, delta.get("DIAMOND"));
        assertEquals(-16, delta.get("STONE"));
        assertEquals(8, delta.get("APPLE"));
        assertEquals(Map.of("DIAMOND", 64, "APPLE", 8), MaterialDiff.positive(delta));
        assertEquals(Map.of("STONE", 16), MaterialDiff.negative(delta));
    }

    @Test
    void ignoresAirAndUnchangedCounts() {
        Map<String, Integer> before = Map.of("AIR", 1, "DIRT", 32);
        Map<String, Integer> after = Map.of("AIR", 9, "DIRT", 32);
        assertTrue(MaterialDiff.subtract(after, before).isEmpty());
    }
}
