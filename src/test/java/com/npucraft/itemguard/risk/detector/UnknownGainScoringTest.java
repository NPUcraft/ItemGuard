package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.UnknownGainScorer;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.session.PlayerGuardSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownGainScoringTest {

    private final ItemValueRegistry values = new ItemValueRegistry(1, Map.of(
            "STONE", 1,
            "DIRT", 1,
            "DIAMOND", 25,
            "NETHERITE_BLOCK", 80
    ));
    private final RiskSettings settings = RiskSettings.defaults();
    private final UnexplainedGainDetector detector = new UnexplainedGainDetector(settings, values);

    @Test
    void oneStoneUnknownIsLowRisk() {
        assertEquals(3, score("STONE", 1));
        assertTrue(score("STONE", 1) <= 5);
    }

    @Test
    void lowValueBulkIsNotCritical() {
        assertEquals(3, score("DIRT", 422));
        assertTrue(score("DIRT", 422) < 30);
    }

    @Test
    void diamondUnknownScoresHigherThanStone() {
        assertTrue(score("DIAMOND", 64) > score("STONE", 1));
        assertEquals(25, score("DIAMOND", 64));
    }

    @Test
    void netheriteUnknownScoresHigherThanDiamond() {
        assertTrue(score("NETHERITE_BLOCK", 64) > score("DIAMOND", 64));
        assertEquals(35, score("NETHERITE_BLOCK", 64));
    }

    @Test
    void weightedUnknownRiskClamps() {
        assertEquals(35, score("NETHERITE_BLOCK", 1728));
        assertTrue(UnknownGainScorer.score("NETHERITE_BLOCK", 1728, values, settings.unknownGain()) <= 35);
    }

    @Test
    void unknownPlusHighValueBurstSameIncidentCombines() {
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        ItemFlowEvent diamonds = unknown("DIAMOND", 64);
        List<RiskSignal> unknownSignals = detector.detect(session, List.of(diamonds));
        BurstDetector burst = new BurstDetector(settings, values);
        burst.recordFlow(session, diamonds);
        List<RiskSignal> burstSignals = burst.detect(session, List.of(diamonds));
        int combined = unknownSignals.getFirst().scoreContribution()
                + burstSignals.stream()
                .filter(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST)
                .mapToInt(RiskSignal::scoreContribution)
                .findFirst()
                .orElse(0);
        assertTrue(combined > unknownSignals.getFirst().scoreContribution());
        assertTrue(combined < 80);
    }

    @Test
    void detectorEmitsValueDrivenScoreNotFlatThirtyFive() {
        PlayerGuardSession session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        List<RiskSignal> stone = detector.detect(session, List.of(unknown("STONE", 1)));
        assertEquals(1, stone.size());
        assertEquals(3, stone.getFirst().scoreContribution());
    }

    private int score(String material, int amount) {
        return UnknownGainScorer.score(material, amount, values, settings.unknownGain());
    }

    private static ItemFlowEvent unknown(String material, int amount) {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "player-1",
                Instant.now(),
                material,
                ItemSignature.materialOnly(material),
                amount,
                FlowSource.UNKNOWN,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.UNKNOWN,
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
