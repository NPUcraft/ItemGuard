package dev.itemguard.flow.model;

import dev.itemguard.item.ItemSignature;

import java.util.Map;

public record SlotSnapshot(
        SlotArea area,
        int index,
        String material,
        int amount,
        ItemSignature signature,
        boolean empty,
        boolean container,
        Map<String, Integer> nestedMaterials
) {
    public SlotSnapshot {
        nestedMaterials = nestedMaterials == null ? Map.of() : Map.copyOf(nestedMaterials);
    }

    public static SlotSnapshot empty(SlotArea area, int index) {
        return new SlotSnapshot(area, index, "AIR", 0, ItemSignature.materialOnly("AIR"), true, false, Map.of());
    }

    public String signatureKey() {
        ItemSignature sig = signature == null ? ItemSignature.materialOnly(material) : signature;
        return sig.material() + "|" + sig.hash();
    }
}
