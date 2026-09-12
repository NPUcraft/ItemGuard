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

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShulkerFlowDetectorTest {

    private final ItemValueRegistry values = new ItemValueRegistry(1, Map.of("DIRT", 1, "DIAMOND", 25));
    private final ShulkerFlowDetector detector = new ShulkerFlowDetector(RiskSettings.defaults(), values);

    @Test
    void verifiedRapidShulkerAloneLowOrZeroRisk() {
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent dirt = flow("DIRT", 64, FlowSource.SHULKER_BOX, SourceConfidence.VERIFIED);
        detector.detect(session, List.of(dirt));
        List<RiskSignal> rapid = detector.detect(session, List.of(dirt));
        int score = rapid.stream().mapToInt(RiskSignal::scoreContribution).sum();
        assertTrue(score <= 5);
    }

    @Test
    void rapidShulkerWithUnknownCanIncreaseIncident() {
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent shulker = flow("DIAMOND", 64, FlowSource.SHULKER_BOX, SourceConfidence.VERIFIED);
        ItemFlowEvent unknown = flow("DIAMOND", 64, FlowSource.UNKNOWN, SourceConfidence.UNKNOWN);
        detector.detect(session, List.of(shulker, unknown));
        List<RiskSignal> rapid = detector.detect(session, List.of(shulker, unknown));
        assertTrue(rapid.stream().anyMatch(signal ->
                signal.type() == SignalType.SHULKER_RAPID_TRANSFER && signal.scoreContribution() > 0));
    }

    private static ItemFlowEvent flow(String material, int amount, FlowSource source, SourceConfidence confidence) {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "player-1",
                Instant.now(),
                material,
                ItemSignature.materialOnly(material),
                amount,
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
