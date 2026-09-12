package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.session.PlayerGuardSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BurstDetectorTest {

    @Test
    void slotMovesWithNoGainDoNotReEmitBurst() {
        assertFalse(BurstDetector.hasIncomingGain(List.of()));
        assertFalse(BurstDetector.hasIncomingGain(List.of(flow("DIAMOND", 0))));
        assertFalse(BurstDetector.hasIncomingGain(List.of(flow("DIAMOND", -64))));
    }

    @Test
    void realIncomingGainsStillQualify() {
        assertTrue(BurstDetector.hasIncomingGain(List.of(flow("DIAMOND", 64))));
    }

    @Test
    void huskSyncApplyDoesNotCreateHighValueBurst() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "ItemGuardBot", Instant.now());
        ItemFlowEvent diamonds = huskSync("DIAMOND", 64);
        ItemFlowEvent elytra = huskSync("ELYTRA", 1);
        detector.recordFlow(session, diamonds);
        detector.recordFlow(session, elytra);
        List<RiskSignal> signals = detector.detect(session, List.of(diamonds, elytra));
        assertTrue(signals.stream().noneMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
        assertTrue(session.gainWindow().recent(10).isEmpty());
    }

    @Test
    void unexplainedHighValueGainStillBursts() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "ItemGuardBot", Instant.now());
        ItemFlowEvent diamonds = event("DIAMOND", 64, FlowSource.UNKNOWN, SourceConfidence.UNKNOWN);
        detector.recordFlow(session, diamonds);
        List<RiskSignal> signals = detector.detect(session, List.of(diamonds));
        assertTrue(signals.stream().anyMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    @Test
    void verifiedChestDiamondsDoNotTriggerHighValueBurst() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent diamonds = event("DIAMOND", 64, FlowSource.CONTAINER, SourceConfidence.VERIFIED);
        detector.recordFlow(session, diamonds);
        assertTrue(detector.detect(session, List.of(diamonds)).stream()
                .noneMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    @Test
    void dirtDoesNotTriggerHighValueBurst() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent dirt = flow("DIRT", 422);
        detector.recordFlow(session, dirt);
        List<RiskSignal> signals = detector.detect(session, List.of(dirt));
        assertTrue(signals.stream().noneMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    @Test
    void stoneDoesNotTriggerHighValueBurst() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent stone = flow("STONE", 500);
        detector.recordFlow(session, stone);
        assertTrue(detector.detect(session, List.of(stone)).stream()
                .noneMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    @Test
    void diamondCanTriggerHighValueBurst() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent diamonds = event("DIAMOND", 64, FlowSource.UNKNOWN, SourceConfidence.UNKNOWN);
        detector.recordFlow(session, diamonds);
        assertTrue(detector.detect(session, List.of(diamonds)).stream()
                .anyMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    @Test
    void repeatedDirtDoesNotTriggerRareIdentical() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemSignature signature = new ItemSignature("DIRT", "dirt-hash-123456");
        ItemFlowEvent dirt = event("DIRT", 128, FlowSource.PLAYER_INVENTORY, SourceConfidence.VERIFIED, signature);
        detector.recordFlow(session, dirt);
        assertTrue(detector.detect(session, List.of(dirt)).stream()
                .noneMatch(signal -> signal.type() == SignalType.REPEATED_IDENTICAL_ITEMS));
    }

    @Test
    void repeatedRareSignatureCanTrigger() {
        BurstDetector detector = detector();
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemSignature signature = new ItemSignature("DIAMOND", "diamond-hash-123456");
        ItemFlowEvent diamonds = event("DIAMOND", 128, FlowSource.UNKNOWN, SourceConfidence.UNKNOWN, signature);
        detector.recordFlow(session, diamonds);
        assertTrue(detector.detect(session, List.of(diamonds)).stream()
                .anyMatch(signal -> signal.type() == SignalType.REPEATED_IDENTICAL_ITEMS));
    }

    private static BurstDetector detector() {
        return new BurstDetector(RiskSettings.defaults(), new ItemValueRegistry(1, Map.of(
                "DIAMOND", 25,
                "ELYTRA", 90,
                "DIRT", 1,
                "STONE", 1
        )));
    }

    private static ItemFlowEvent huskSync(String material, int delta) {
        return event(material, delta, FlowSource.HUSKSYNC_DATA_APPLY, SourceConfidence.TRUSTED, ItemSignature.materialOnly(material));
    }

    private static ItemFlowEvent flow(String material, int delta) {
        return event(material, delta, FlowSource.PLAYER_INVENTORY, SourceConfidence.VERIFIED, ItemSignature.materialOnly(material));
    }

    private static ItemFlowEvent event(String material, int delta, FlowSource source, SourceConfidence confidence) {
        return event(material, delta, source, confidence, ItemSignature.materialOnly(material));
    }

    private static ItemFlowEvent event(
            String material,
            int delta,
            FlowSource source,
            SourceConfidence confidence,
            ItemSignature signature
    ) {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "bot",
                Instant.now(),
                material,
                signature,
                delta,
                source,
                FlowDestination.PLAYER_INVENTORY,
                confidence,
                "world",
                UUID.randomUUID(),
                0,
                100,
                0,
                null,
                "test",
                UUID.randomUUID()
        );
    }
}
