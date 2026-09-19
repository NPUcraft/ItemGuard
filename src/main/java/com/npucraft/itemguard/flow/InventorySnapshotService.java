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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures a detached player-owned inventory: storage/armor/extra, cursor, and owned input slots.
 * Nested bundle/shulker contents are material counts only — no inner SHA-256.
 * External chests, shulkers, hoppers, and furnaces are never included.
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
        addStack(slots, SlotArea.CURSOR, 0, player.getItemOnCursor());
        addOwnedInputs(slots, player);
        return InventorySnapshot.fromSlots(timestamp, slots);
    }

    /**
     * Player-owned crafting/trade input UIs. Result slots are excluded at capture time.
     * Chest/shulker/hopper/furnace inventories must stay false.
     */
    public static boolean isOwnedInputInventory(InventoryType type) {
        return type != null && OwnedInputInventories.isOwned(type.name());
    }

    private void addOwnedInputs(List<SlotSnapshot> slots, Player player) {
        InventoryView view;
        try {
            view = player.getOpenInventory();
        } catch (RuntimeException ignored) {
            return;
        }
        if (view == null) {
            return;
        }
        Inventory top;
        try {
            top = view.getTopInventory();
        } catch (RuntimeException ignored) {
            return;
        }
        if (top == null || top instanceof PlayerInventory || !OwnedInputInventories.isOwned(top.getType().name())) {
            return;
        }
        int size = top.getSize();
        for (int index = 0; index < size; index++) {
            InventoryType.SlotType slotType = InventoryType.SlotType.CONTAINER;
            try {
                slotType = view.getSlotType(index);
            } catch (RuntimeException ignored) {
                // Treat unknown as an input slot rather than skipping the whole view.
            }
            if (slotType == InventoryType.SlotType.RESULT) {
                continue;
            }
            ItemStack stack;
            try {
                stack = top.getItem(index);
            } catch (RuntimeException ignored) {
                continue;
            }
            addStack(slots, SlotArea.OWNED_INPUT, index, stack);
        }
    }

    private void addArea(List<SlotSnapshot> slots, SlotArea area, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (int index = 0; index < contents.length; index++) {
            addStack(slots, area, index, contents[index]);
        }
    }

    private void addStack(List<SlotSnapshot> slots, SlotArea area, int index, ItemStack stack) {
        if (Items.isEmpty(stack)) {
            slots.add(SlotSnapshot.empty(area, index));
            return;
        }
        int maxDepth = settings.maxScanDepth();
        String material = stack.getType().name();
        int value = values.valueOf(material);
        boolean container = NestedContents.isContainerItem(stack);
        java.util.Map<String, Integer> nested = container ? NestedContents.materialTotals(stack, maxDepth) : java.util.Map.of();
        ItemSignature signature = signatures.shouldHash(stack, value, settings)
                ? signatures.of(stack)
                : ItemSignature.materialOnly(material);
        slots.add(new SlotSnapshot(area, index, material, stack.getAmount(), signature, false, container, nested));
    }
}
