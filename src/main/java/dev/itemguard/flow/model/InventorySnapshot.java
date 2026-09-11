package dev.itemguard.flow.model;

import dev.itemguard.item.ItemSignature;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detached player inventory view. Never holds a live Inventory reference.
 * {@code materialTotals} is top-level only. Nested bundle/shulker contents live in {@code nestedTotals}.
 */
public record InventorySnapshot(
        Instant timestamp,
        Map<String, Integer> materialTotals,
        Map<String, Integer> nestedTotals,
        Map<ItemSignature, Integer> signatureTotals,
        List<SlotSnapshot> slots
) {
    public InventorySnapshot {
        Objects.requireNonNull(timestamp, "timestamp");
        materialTotals = Map.copyOf(materialTotals);
        nestedTotals = nestedTotals == null ? Map.of() : Map.copyOf(nestedTotals);
        signatureTotals = Map.copyOf(signatureTotals);
        slots = List.copyOf(slots);
    }

    public static InventorySnapshot empty(Instant timestamp) {
        return new InventorySnapshot(timestamp, Map.of(), Map.of(), Map.of(), List.of());
    }

    public int materialCount(String material) {
        return materialTotals.getOrDefault(material, 0);
    }
}
