package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.flow.model.SlotArea;
import com.npucraft.itemguard.flow.model.SlotSnapshot;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemSignatures;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.item.NestedContents;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a detached player inventory using storage/armor/extra once each.
 * Nested bundle/shulker contents are material counts only — no inner SHA-256.
 */
public final class InventorySnapshotService {

    private final ItemSignatures signatures;
    private final ItemValueRegistry values;
    private volatile PluginSettings settings;

    public InventorySnapshotService(ItemSignatures signatures, ItemValueRegistry values, PluginSettings settings) {
        this.signatures = signatures;
        this.values = values;
        this.settings = settings;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public InventorySnapshot capture(Player player, Instant timestamp) {
        PlayerInventory inventory = player.getInventory();
        List<SlotSnapshot> slots = new ArrayList<>();
        addArea(slots, SlotArea.STORAGE, inventory.getStorageContents());
        addArea(slots, SlotArea.ARMOR, inventory.getArmorContents());
        addArea(slots, SlotArea.EXTRA, inventory.getExtraContents());

        Map<String, Integer> materials = new HashMap<>();
        Map<String, Integer> nested = new HashMap<>();
        Map<ItemSignature, Integer> signatureTotals = new HashMap<>();
        for (SlotSnapshot slot : slots) {
            if (slot.empty()) {
                continue;
            }
            materials.merge(slot.material(), slot.amount(), Integer::sum);
            slot.nestedMaterials().forEach((material, amount) -> nested.merge(material, amount, Integer::sum));
            if (slot.signature() != null && slot.signature().isDetailed()) {
                signatureTotals.merge(slot.signature(), slot.amount(), Integer::sum);
            }
        }
        return new InventorySnapshot(timestamp, materials, nested, signatureTotals, slots);
    }

    private void addArea(List<SlotSnapshot> slots, SlotArea area, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        int maxDepth = settings.maxScanDepth();
        for (int index = 0; index < contents.length; index++) {
            ItemStack stack = contents[index];
            if (Items.isEmpty(stack)) {
                slots.add(SlotSnapshot.empty(area, index));
                continue;
            }
            String material = stack.getType().name();
            int value = values.valueOf(material);
            boolean container = NestedContents.isContainerItem(stack);
            Map<String, Integer> nested = container ? NestedContents.materialTotals(stack, maxDepth) : Map.of();
            ItemSignature signature = signatures.shouldHash(stack, value, settings)
                    ? signatures.of(stack)
                    : ItemSignature.materialOnly(material);
            slots.add(new SlotSnapshot(area, index, material, stack.getAmount(), signature, false, container, nested));
        }
    }
}
