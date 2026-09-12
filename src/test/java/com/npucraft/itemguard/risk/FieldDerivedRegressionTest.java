package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.ExpectedFlowCredit;
import com.npucraft.itemguard.flow.ExpectedFlowLedger;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.detector.BurstDetector;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanFinding;
import com.npucraft.itemguard.scan.ScannerFindingObserver;
import com.npucraft.itemguard.session.PlayerGuardSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIELD-DERIVED REGRESSION patterns. Not a full server replay.
 */
class FieldDerivedRegressionTest {

    @Test
    void scannerRepeatBootsDoesNotStack() {
        ScannerFindingObserver observer = new ScannerFindingObserver(RiskSettings.defaults());
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        UUID player = UUID.fromString("00000000-0000-4000-8000-000000000001");
        ScanFinding finding = new ScanFinding(
                "enchantments",
                SignalType.CONFLICTING_ENCHANTMENTS,
                ScanClassification.SUSPICIOUS,
                Severity.SUSPICIOUS,
                "NETHERITE_BOOTS",
                new ItemSignature("NETHERITE_BOOTS", "fake-boots-hash"),
                1,
                "conflicting enchantment",
                Map.of()
        );
        int produced = 0;
        Instant now = Instant.now();
        for (int i = 0; i < 50; i++) {
            var signals = observer.observe(player, List.of(finding), UUID.randomUUID(), now.plusMillis(i), engine.incidents());
            produced += signals.size();
            engine.assess(player, signals, now.plusMillis(i));
        }
        assertEquals(1, produced);
        assertEquals(1, engine.incidents().active(player, now.plusSeconds(1)).size());
        assertTrue(engine.incidents().currentRisk(player, now.plusSeconds(1)) < 60);
    }

    @Test
    void lowValueDirtBurstIsNotHighValue() {
        ItemValueRegistry values = new ItemValueRegistry(1, Map.of("DIRT", 1));
        BurstDetector burst = new BurstDetector(RiskSettings.defaults(), values);
        PlayerGuardSession session = new PlayerGuardSession(
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "player-1", Instant.now());
        ItemFlowEvent dirt = new ItemFlowEvent(
                UUID.randomUUID(), session.playerId(), "player-1", Instant.now(), "DIRT",
                ItemSignature.materialOnly("DIRT"), 422, FlowSource.CONTAINER, FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED, "world", UUID.randomUUID(), 0, 100, 0, null, "test", UUID.randomUUID()
        );
        burst.recordFlow(session, dirt);
        assertTrue(burst.detect(session, List.of(dirt)).stream()
                .noneMatch(signal -> signal.type() == SignalType.HIGH_VALUE_ITEM_BURST));
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        RiskAssessment assessment = engine.assess(session.playerId(), burst.detect(session, List.of(dirt)), Instant.now());
        assertTrue(assessment.score() < 60);
    }

    @Test
    void craftHintDoesNotExplainLaterUnrelatedGain() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Instant now = Instant.now();
        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(), player, "DIAMOND", null, 10_000, FlowSource.CRAFTING,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.INFERRED, now, now.plusMillis(250),
                UUID.randomUUID(), "craft", null, null, "vanilla", true
        ));
        assertEquals(9, ledger.consume(player, "DIAMOND", 9, now).getFirst().amount());
        assertTrue(ledger.consume(player, "COBBLESTONE", 8, now.plusMillis(100)).isEmpty());
    }

    @Test
    void unrelatedEventsDoNotSaturateBySum() {
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        UUID player = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Instant now = Instant.now();
        RiskAssessment assessment = engine.assess(player, List.of(
                new com.npucraft.itemguard.risk.model.RiskSignal(
                        UUID.randomUUID(), player, SignalType.CONFLICTING_ENCHANTMENTS, 10, Severity.SUSPICIOUS,
                        now, now.plusSeconds(60), Map.of(), "boots", "NETHERITE_BOOTS",
                        new ItemSignature("NETHERITE_BOOTS", "fake-boots-hash"), 1, UUID.randomUUID(), null
                ),
                new com.npucraft.itemguard.risk.model.RiskSignal(
                        UUID.randomUUID(), player, SignalType.UNEXPLAINED_ITEM_GAIN, 3, Severity.INFO,
                        now, now.plusSeconds(60), Map.of(), "stone", "STONE",
                        ItemSignature.materialOnly("STONE"), 2, UUID.randomUUID(), null
                )
        ), now);
        assertEquals(10, assessment.score());
        assertFalse(assessment.score() == 13);
        assertTrue(assessment.activeIncidents().size() >= 2);
    }
}
