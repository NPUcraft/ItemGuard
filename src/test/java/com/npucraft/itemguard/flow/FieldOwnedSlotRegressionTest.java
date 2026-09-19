package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.flow.model.SlotArea;
import com.npucraft.itemguard.flow.model.SlotSnapshot;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field-derived owned-slot / bucket / merchant leftover reproductions.
 */
class FieldOwnedSlotRegressionTest {

    @Test
    void emptyingFilledBucketLooksLikeEmptyBucketGainUntilCredited() {
        Instant now = Instant.now();
        InventorySnapshot before = InventorySnapshot.fromSlots(now, List.of(item(SlotArea.STORAGE, 0, "WATER_BUCKET", 1)));
        InventorySnapshot after = InventorySnapshot.fromSlots(now, List.of(item(SlotArea.STORAGE, 0, "BUCKET", 1)));
        Map<String, Integer> gains = InventoryEconomics.flowGains(before, after);
        assertEquals(1, gains.get("BUCKET"));
        assertFalse(gains.containsKey("WATER_BUCKET"));

        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        UnknownGainCorrelator correlator = new UnknownGainCorrelator();
        assertEquals(1, correlator.correlate(player, gains, ledger, now).unexplainedAmount());

        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(), player, "BUCKET", null, 1, FlowSource.PLAYER_INVENTORY,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.VERIFIED, now, now.plusSeconds(3),
                UUID.randomUUID(), "bucket-empty", null, null, "vanilla", false
        ));
        assertTrue(correlator.correlate(player, gains, ledger, now).unexplained().isEmpty());
    }

    @Test
    void cursorMoveWithinOwnedSlotsIsZeroGain() {
        Instant now = Instant.now();
        InventorySnapshot storage = InventorySnapshot.fromSlots(now, List.of(item(SlotArea.STORAGE, 0, "DIAMOND", 1)));
        InventorySnapshot cursor = InventorySnapshot.fromSlots(now, List.of(item(SlotArea.CURSOR, 0, "DIAMOND", 1)));
        assertTrue(InventoryEconomics.flowGains(storage, cursor).isEmpty());
        assertTrue(InventoryEconomics.flowGains(cursor, storage).isEmpty());
    }

    @Test
    void omittingCursorMakesPutBackLookLikeUnknownGain() {
        Instant now = Instant.now();
        InventorySnapshot storage = InventorySnapshot.fromSlots(now, List.of(item(SlotArea.STORAGE, 0, "DIAMOND", 1)));
        InventorySnapshot emptyStorage = InventorySnapshot.fromSlots(now, List.of());
        assertTrue(InventoryEconomics.flowGains(storage, emptyStorage).isEmpty());
        assertEquals(1, InventoryEconomics.flowGains(emptyStorage, storage).get("DIAMOND"));
    }

    @Test
    void merchantLeftoverReturningFromOwnedInputIsZeroGain() {
        Instant now = Instant.now();
        InventorySnapshot trading = InventorySnapshot.fromSlots(now, List.of(
                item(SlotArea.STORAGE, 0, "EMERALD", 9),
                item(SlotArea.OWNED_INPUT, 0, "BOOK", 8),
                item(SlotArea.OWNED_INPUT, 1, "EMERALD", 44)
        ));
        InventorySnapshot closed = InventorySnapshot.fromSlots(now, List.of(
                item(SlotArea.STORAGE, 0, "EMERALD", 53),
                item(SlotArea.STORAGE, 1, "BOOK", 8)
        ));
        assertTrue(InventoryEconomics.flowGains(trading, closed).isEmpty());
    }

    @Test
    void huskSyncThenCursorShuffleOfSameKeysIsZeroGain() {
        Instant now = Instant.now();
        InventorySnapshot afterApply = InventorySnapshot.fromSlots(now, List.of(
                item(SlotArea.STORAGE, 0, "OMINOUS_TRIAL_KEY", 63)
        ));
        InventorySnapshot onCursor = InventorySnapshot.fromSlots(now, List.of(
                item(SlotArea.CURSOR, 0, "OMINOUS_TRIAL_KEY", 63)
        ));
        assertTrue(InventoryEconomics.flowGains(afterApply, onCursor).isEmpty());
        assertTrue(InventoryEconomics.flowGains(onCursor, afterApply).isEmpty());
    }

    @Test
    void externalChestsAreNotOwnedInputInventories() {
        assertFalse(OwnedInputInventories.isOwned("CHEST"));
        assertFalse(OwnedInputInventories.isOwned("ENDER_CHEST"));
        assertFalse(OwnedInputInventories.isOwned("SHULKER_BOX"));
        assertFalse(OwnedInputInventories.isOwned("BARREL"));
        assertFalse(OwnedInputInventories.isOwned("HOPPER"));
        assertFalse(OwnedInputInventories.isOwned("FURNACE"));
        assertFalse(OwnedInputInventories.isOwned("CRAFTER"));
        assertTrue(OwnedInputInventories.isOwned("MERCHANT"));
        assertTrue(OwnedInputInventories.isOwned("WORKBENCH"));
        assertTrue(OwnedInputInventories.isOwned("CRAFTING"));
    }

    @Test
    void unknownDiagnosisUsesPreConsumeHintSnapshot() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(), player, "DIAMOND", null, 10_000, FlowSource.CONTAINER,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.VERIFIED, now, now.plusMillis(250),
                UUID.randomUUID(), "container-click", null, null, "vanilla", true
        ));
        ExpectedFlowLedger.Probe probe = ledger.probe(player, now);
        assertEquals(1, probe.hintCount());
        UnknownGainCorrelator.CorrelationResult result = new UnknownGainCorrelator().correlate(
                player, Map.of("DIAMOND", 1, "BUCKET", 1), ledger, now
        );
        assertEquals(1, result.unexplained().size());
        assertEquals("BUCKET", result.unexplained().getFirst().material());
        assertEquals(0, ledger.hintCount(player, now));
        assertEquals(1, probe.hintCount());
        assertEquals(0, probe.matchingHintCount("BUCKET"));
        assertEquals(1, probe.matchingHintCount("DIAMOND"));
        AttributionDecision decision = new AttributionDecision(
                now, "unexplained", "BUCKET", 1, probe.nearestHint().source().name(), "SOURCE_HINT",
                probe.hintCount(), probe.exactCount(), probe.nearestHint().source().name(), 0, 1,
                probe.matchingHintCount("BUCKET"), result.explained().size()
        );
        assertEquals("1", decision.toMetadata().get("pendingHintsCount"));
        assertEquals("0", decision.toMetadata().get("matchingHintCount"));
        assertEquals("pre-consume", decision.toMetadata().get("diagnosisStage"));
    }

    private static SlotSnapshot item(SlotArea area, int index, String material, int amount) {
        return new SlotSnapshot(
                area, index, material, amount, ItemSignature.materialOnly(material), false, false, Map.of()
        );
    }
}
