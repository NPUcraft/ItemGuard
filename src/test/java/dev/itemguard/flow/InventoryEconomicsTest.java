package dev.itemguard.flow;

import dev.itemguard.flow.model.InventorySnapshot;
import dev.itemguard.flow.model.SlotArea;
import dev.itemguard.flow.model.SlotSnapshot;
import dev.itemguard.item.ItemSignature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryEconomicsTest {

    @Test
    void acquiringFilledShulkerCountsBoxNotInnerStacks() {
        Instant now = Instant.now();
        InventorySnapshot before = InventorySnapshot.empty(now);
        SlotSnapshot shulker = new SlotSnapshot(
                SlotArea.STORAGE,
                0,
                "SHULKER_BOX",
                1,
                new ItemSignature("SHULKER_BOX", "box-a"),
                false,
                true,
                Map.of("DIAMOND", 1728)
        );
        InventorySnapshot after = new InventorySnapshot(
                now,
                Map.of("SHULKER_BOX", 1),
                Map.of("DIAMOND", 1728),
                Map.of(),
                List.of(shulker)
        );
        Map<String, Integer> gains = InventoryEconomics.flowGains(before, after);
        assertEquals(1, gains.get("SHULKER_BOX"));
        assertFalse(gains.containsKey("DIAMOND"));
    }

    @Test
    void movingFilledShulkerBetweenSlotsIsZeroGain() {
        Instant now = Instant.now();
        SlotSnapshot a = new SlotSnapshot(
                SlotArea.STORAGE, 0, "SHULKER_BOX", 1,
                new ItemSignature("SHULKER_BOX", "box-a"), false, true, Map.of("DIAMOND", 1728)
        );
        SlotSnapshot b = new SlotSnapshot(
                SlotArea.STORAGE, 1, "SHULKER_BOX", 1,
                new ItemSignature("SHULKER_BOX", "box-a"), false, true, Map.of("DIAMOND", 1728)
        );
        InventorySnapshot before = new InventorySnapshot(now, Map.of("SHULKER_BOX", 1), Map.of("DIAMOND", 1728), Map.of(), List.of(a));
        InventorySnapshot after = new InventorySnapshot(now, Map.of("SHULKER_BOX", 1), Map.of("DIAMOND", 1728), Map.of(), List.of(b));
        assertTrue(InventoryEconomics.flowGains(before, after).isEmpty());
    }

    @Test
    void diamondsLeavingTopLevelIntoOwnedBundleAreNotALossGainCycle() {
        Instant now = Instant.now();
        SlotSnapshot diamonds = new SlotSnapshot(
                SlotArea.STORAGE, 0, "DIAMOND", 64,
                ItemSignature.materialOnly("DIAMOND"), false, false, Map.of()
        );
        SlotSnapshot emptyBundle = new SlotSnapshot(
                SlotArea.STORAGE, 1, "BUNDLE", 1,
                new ItemSignature("BUNDLE", "b1"), false, true, Map.of()
        );
        SlotSnapshot filledBundle = new SlotSnapshot(
                SlotArea.STORAGE, 1, "BUNDLE", 1,
                new ItemSignature("BUNDLE", "b1"), false, true, Map.of("DIAMOND", 64)
        );
        InventorySnapshot before = new InventorySnapshot(
                now, Map.of("DIAMOND", 64, "BUNDLE", 1), Map.of(), Map.of(), List.of(diamonds, emptyBundle)
        );
        InventorySnapshot after = new InventorySnapshot(
                now, Map.of("BUNDLE", 1), Map.of("DIAMOND", 64), Map.of(), List.of(filledBundle)
        );
        assertTrue(InventoryEconomics.flowGains(before, after).isEmpty());
    }

    @Test
    void recursionDepthGuard() {
        assertTrue(InventoryEconomics.canRecurse(0, 3));
        assertTrue(InventoryEconomics.canRecurse(2, 3));
        assertFalse(InventoryEconomics.canRecurse(3, 3));
        assertEquals(3, InventoryEconomics.nestedDepthGuard(9, 3));
        assertEquals(0, InventoryEconomics.nestedDepthGuard(-2, 3));
    }
}
