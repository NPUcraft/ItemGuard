package dev.itemguard.risk.detector;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.flow.model.SourceConfidence;
import dev.itemguard.item.ItemSignature;
import dev.itemguard.item.ItemValueRegistry;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.session.PlayerGuardSession;
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
        ItemFlowEvent diamonds = flow("DIAMOND", 64);
        detector.recordFlow(session, diamonds);
        List<RiskSignal> signals = detector.detect(session, List.of(diamonds));
        assertTrue(signals.stream().anyMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
    }

    private static BurstDetector detector() {
        return new BurstDetector(RiskSettings.defaults(), new ItemValueRegistry(1, Map.of("DIAMOND", 25, "ELYTRA", 90)));
    }

    private static ItemFlowEvent huskSync(String material, int delta) {
        return event(material, delta, FlowSource.HUSKSYNC_DATA_APPLY, SourceConfidence.TRUSTED);
    }

    private static ItemFlowEvent flow(String material, int delta) {
        return event(material, delta, FlowSource.PLAYER_INVENTORY, SourceConfidence.VERIFIED);
    }

    private static ItemFlowEvent event(String material, int delta, FlowSource source, SourceConfidence confidence) {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "bot",
                Instant.now(),
                material,
                ItemSignature.materialOnly(material),
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
