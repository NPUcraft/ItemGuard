package com.npucraft.itemguard.flow.model;

import com.npucraft.itemguard.item.ItemSignature;

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

    /**
     * Builds totals from player-owned slots only. Callers must not pass external chest contents.
     */
    public static InventorySnapshot fromSlots(Instant timestamp, List<SlotSnapshot> slots) {
        List<SlotSnapshot> copy = slots == null ? List.of() : List.copyOf(slots);
        Map<String, Integer> materials = new java.util.HashMap<>();
        Map<String, Integer> nested = new java.util.HashMap<>();
        Map<ItemSignature, Integer> signatureTotals = new java.util.HashMap<>();
        for (SlotSnapshot slot : copy) {
            if (slot == null || slot.empty()) {
                continue;
            }
            materials.merge(slot.material(), slot.amount(), Integer::sum);
            slot.nestedMaterials().forEach((material, amount) -> nested.merge(material, amount, Integer::sum));
            if (slot.signature() != null && slot.signature().isDetailed()) {
                signatureTotals.merge(slot.signature(), slot.amount(), Integer::sum);
            }
        }
        return new InventorySnapshot(timestamp, materials, nested, signatureTotals, copy);
    }

    public int materialCount(String material) {
        return materialTotals.getOrDefault(material, 0);
    }
}
