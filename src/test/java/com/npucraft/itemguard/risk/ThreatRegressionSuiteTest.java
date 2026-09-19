package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueDefaults;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.detector.BurstDetector;
import com.npucraft.itemguard.risk.detector.UnexplainedGainDetector;
import com.npucraft.itemguard.risk.incident.IncidentAlertLevel;
import com.npucraft.itemguard.risk.incident.IncidentType;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanFinding;
import com.npucraft.itemguard.session.PlayerGuardSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatRegressionSuiteTest {

    @Test
    void threatScenariosRemainDetectable() {
        ItemValueRegistry values = new ItemValueRegistry(1, Map.of(
                "DIAMOND", 25,
                "NETHERITE_BLOCK", 80,
                "DIAMOND_SWORD", 20
        ));
        RiskSettings settings = RiskSettings.defaults();
        int detected = 0;
        int missed = 0;

        if (overLevelDetected()) {
            detected++;
        } else {
            missed++;
        }
        if (oversizedDetected()) {
            detected++;
        } else {
            missed++;
        }
        if (diamondUnknownMeaningful(values, settings)) {
            detected++;
        } else {
            missed++;
        }
        if (netheriteUnknownHigherThanDiamond(values, settings)) {
            detected++;
        } else {
            missed++;
        }
        if (weightedChestplateUnknownStillDetectable()) {
            detected++;
        } else {
            missed++;
        }
        if (criticalDupeLikeSample(values, settings)) {
            detected++;
        } else {
            missed++;
        }
        assertEquals(0, missed, "threat samples missed=" + missed + " detected=" + detected);
        assertEquals(6, detected);
    }

    private static boolean overLevelDetected() {
        ScanFinding finding = new ScanFinding(
                "enchantments",
                SignalType.OVER_LEVEL_ENCHANTMENT,
                ScanClassification.INVALID,
                Severity.INVALID,
                "DIAMOND_SWORD",
                new ItemSignature("DIAMOND_SWORD", "sword-255"),
                1,
                "sharpness 255",
                Map.of("level", "255")
        );
        return finding.classification() == ScanClassification.INVALID
                && RiskSettings.defaults().score(SignalType.OVER_LEVEL_ENCHANTMENT) > 0;
    }

    private static boolean oversizedDetected() {
        return RiskSettings.defaults().score(SignalType.OVERSIZED_STACK) >= 40;
    }

    private static boolean diamondUnknownMeaningful(ItemValueRegistry values, RiskSettings settings) {
        Replay replay = replay(values, settings);
        RiskAssessment assessment = replay.unknown("DIAMOND", 64);
        return assessment.score() >= 20 && assessment.score() < 80;
    }

    private static boolean netheriteUnknownHigherThanDiamond(ItemValueRegistry values, RiskSettings settings) {
        int diamond = replay(values, settings).unknown("DIAMOND", 64).score();
        int netherite = replay(values, settings).unknown("NETHERITE_BLOCK", 64).score();
        return netherite > diamond;
    }

    private static boolean weightedChestplateUnknownStillDetectable() {
        ItemValueRegistry values = new ItemValueRegistry(1, ItemValueDefaults.mergeOperatorValues(Map.of(
                "DIAMOND", 25,
                "NETHERITE_BLOCK", 80
        )));
        RiskAssessment assessment = replay(values, RiskSettings.defaults()).unknown("NETHERITE_CHESTPLATE", 1);
        return assessment.score() >= 10 && assessment.score() < 60;
    }

    private static boolean criticalDupeLikeSample(ItemValueRegistry values, RiskSettings settings) {
        Replay replay = replay(values, settings);
        UUID player = replay.session.playerId();
        Instant now = Instant.now();
        ItemSignature signature = new ItemSignature("NETHERITE_BLOCK", "netherite-block-identical");
        ItemFlowEvent gain = new ItemFlowEvent(
                UUID.randomUUID(), player, "player-1", now, "NETHERITE_BLOCK", signature, 1728,
                FlowSource.UNKNOWN, FlowDestination.PLAYER_INVENTORY, SourceConfidence.UNKNOWN,
                "world", UUID.randomUUID(), 0, 100, 0, null, "test", UUID.randomUUID()
        );
        RiskAssessment assessment = replay.play(List.of(gain));
        boolean types = assessment.contributingSignals().stream().map(RiskSignal::type).toList()
                .containsAll(List.of(SignalType.UNEXPLAINED_ITEM_GAIN, SignalType.HIGH_VALUE_ITEM_BURST, SignalType.REPEATED_IDENTICAL_ITEMS));
        boolean critical = assessment.score() >= 80
                && assessment.escalations().stream().anyMatch(e -> e.to() == IncidentAlertLevel.CRITICAL)
                && assessment.escalations().size() == 1
                && assessment.activeIncidents().stream().anyMatch(incident -> incident.type() == IncidentType.ITEM_GAIN);
        return types && critical;
    }

    private static Replay replay(ItemValueRegistry values, RiskSettings settings) {
        return new Replay(values, settings);
    }

    static final class Replay {
        final RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        final UnexplainedGainDetector unexplained;
        final BurstDetector burst;
        final PlayerGuardSession session;

        Replay(ItemValueRegistry values, RiskSettings settings) {
            this.unexplained = new UnexplainedGainDetector(settings, values);
            this.burst = new BurstDetector(settings, values);
            this.session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        }

        RiskAssessment unknown(String material, int amount) {
            ItemFlowEvent flow = new ItemFlowEvent(
                    UUID.randomUUID(), session.playerId(), "player-1", Instant.now(), material,
                    new ItemSignature(material, material.toLowerCase() + "-sig"), amount,
                    FlowSource.UNKNOWN, FlowDestination.PLAYER_INVENTORY, SourceConfidence.UNKNOWN,
                    "world", UUID.randomUUID(), 0, 100, 0, null, "test", UUID.randomUUID()
            );
            return play(List.of(flow));
        }

        RiskAssessment play(List<ItemFlowEvent> flows) {
            List<RiskSignal> produced = new ArrayList<>();
            for (ItemFlowEvent flow : flows) {
                burst.recordFlow(session, flow);
            }
            produced.addAll(unexplained.detect(session, flows));
            produced.addAll(burst.detect(session, flows));
            return engine.assess(session.playerId(), produced, Instant.now());
        }
    }
}
