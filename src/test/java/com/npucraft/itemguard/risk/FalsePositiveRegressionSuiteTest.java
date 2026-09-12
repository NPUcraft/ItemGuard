package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.detector.BurstDetector;
import com.npucraft.itemguard.risk.detector.ShulkerFlowDetector;
import com.npucraft.itemguard.risk.detector.UnexplainedGainDetector;
import com.npucraft.itemguard.risk.incident.IncidentAlertLevel;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.session.PlayerGuardSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FalsePositiveRegressionSuiteTest {

    private static final String[] MATERIALS = {
            "STONE", "DIRT", "COBBLESTONE", "OAK_PLANKS", "RAIL", "SAND", "GRAVEL",
            "COBBLESTONE_STAIRS", "STONE_BRICKS", "GRASS_BLOCK"
    };
    private static final FlowSource[] SOURCES = {
            FlowSource.CONTAINER, FlowSource.GROUND_PICKUP, FlowSource.CRAFTING,
            FlowSource.SMELTING, FlowSource.SHULKER_BOX
    };

    @Test
    void normalBehaviorPrecision() {
        ItemValueRegistry values = values();
        RiskSettings settings = RiskSettings.defaults();
        int high = 0;
        int critical = 0;
        int sequences = 0;
        for (String material : MATERIALS) {
            for (FlowSource source : SOURCES) {
                for (int amount : List.of(1, 8, 64)) {
                    sequences++;
                    Replay replay = new Replay(values, settings);
                    ItemFlowEvent flow = replay.flow(material, amount, source, SourceConfidence.VERIFIED);
                    RiskAssessment assessment = replay.play(List.of(flow));
                    if (assessment.level() == RiskLevel.HIGH || assessment.escalations().stream().anyMatch(e -> e.to() == IncidentAlertLevel.HIGH)) {
                        high++;
                    }
                    if (assessment.level() == RiskLevel.CRITICAL || assessment.escalations().stream().anyMatch(e -> e.to() == IncidentAlertLevel.CRITICAL)) {
                        critical++;
                    }
                }
            }
        }
        assertEquals(150, sequences);
        assertEquals(0, critical, "normal sequences must not produce Critical");
        assertEquals(0, high, "normal verified sequences must not produce High");
    }

    @Test
    void dirtBurstStaysBelowAlert() {
        Replay replay = new Replay(values(), RiskSettings.defaults());
        ItemFlowEvent dirt = replay.flow("DIRT", 422, FlowSource.CONTAINER, SourceConfidence.VERIFIED);
        RiskAssessment assessment = replay.play(List.of(dirt));
        assertTrue(assessment.score() < 60);
        assertTrue(assessment.escalations().isEmpty());
    }

    @Test
    void unrelatedShulkerDoesNotIncreaseUnknownIncident() {
        Replay replay = new Replay(values(), RiskSettings.defaults());
        UUID player = replay.session.playerId();
        Instant now = Instant.now();
        ItemFlowEvent unknown = new ItemFlowEvent(
                UUID.randomUUID(), player, "player-1", now, "DIAMOND",
                ItemSignature.materialOnly("DIAMOND"), 64, FlowSource.UNKNOWN,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.UNKNOWN,
                "world", UUID.randomUUID(), 0, 100, 0, null, "test", UUID.randomUUID()
        );
        RiskAssessment first = replay.play(List.of(unknown));
        int unknownScore = first.activeIncidents().stream()
                .filter(incident -> "DIAMOND".equals(incident.material()))
                .mapToInt(incident -> incident.score())
                .findFirst()
                .orElse(first.score());
        ItemFlowEvent shulker = replay.flow("DIRT", 64, FlowSource.SHULKER_BOX, SourceConfidence.VERIFIED);
        replay.play(List.of(shulker));
        replay.play(List.of(shulker));
        int after = replay.engine.incidents().active(player, Instant.now()).stream()
                .filter(incident -> incident.material().equals("DIAMOND"))
                .mapToInt(incident -> incident.score())
                .findFirst()
                .orElse(0);
        assertEquals(unknownScore, after);
    }

    @Test
    void mixedVerifiedThenUnknownStaysBelowAlert() {
        Replay replay = new Replay(values(), RiskSettings.defaults());
        replay.play(List.of(replay.flow("DIAMOND", 64, FlowSource.CONTAINER, SourceConfidence.VERIFIED)));
        replay.play(List.of(replay.flow("DIAMOND", 64, FlowSource.GROUND_PICKUP, SourceConfidence.VERIFIED)));
        RiskAssessment unknown = replay.play(List.of(
                replay.flow("DIAMOND", 32, FlowSource.UNKNOWN, SourceConfidence.UNKNOWN)
        ));
        assertTrue(unknown.score() < 60, "verified diamond history must not promote a later +32 UNKNOWN to High");
        assertTrue(unknown.escalations().isEmpty());
    }

    @Test
    void starPlusZeroIsNotStaffAlertWorthy() {
        Replay replay = new Replay(values(), RiskSettings.defaults());
        RiskAssessment assessment = replay.engine.assess(
                replay.session.playerId(),
                List.of(),
                Instant.now()
        );
        assertTrue(assessment.escalations().isEmpty());
        assertEquals(0, assessment.score());
    }

    private static ItemValueRegistry values() {
        return new ItemValueRegistry(1, Map.ofEntries(
                Map.entry("STONE", 1),
                Map.entry("DIRT", 1),
                Map.entry("COBBLESTONE", 1),
                Map.entry("OAK_PLANKS", 1),
                Map.entry("RAIL", 1),
                Map.entry("SAND", 1),
                Map.entry("GRAVEL", 1),
                Map.entry("COBBLESTONE_STAIRS", 1),
                Map.entry("STONE_BRICKS", 1),
                Map.entry("GRASS_BLOCK", 1),
                Map.entry("DIAMOND", 25),
                Map.entry("NETHERITE_BLOCK", 80)
        ));
    }

    static final class Replay {
        final RiskSettings settings;
        final ItemValueRegistry values;
        final RiskEngine engine;
        final BurstDetector burst;
        final UnexplainedGainDetector unexplained;
        final ShulkerFlowDetector shulker;
        final PlayerGuardSession session;

        Replay(ItemValueRegistry values, RiskSettings settings) {
            this.values = values;
            this.settings = settings;
            this.engine = new RiskEngine(settings);
            this.burst = new BurstDetector(settings, values);
            this.unexplained = new UnexplainedGainDetector(settings, values);
            this.shulker = new ShulkerFlowDetector(settings, values);
            this.session = new PlayerGuardSession(UUID.randomUUID(), "player-1", Instant.now());
        }

        RiskAssessment play(List<ItemFlowEvent> flows) {
            List<RiskSignal> produced = new ArrayList<>();
            for (ItemFlowEvent flow : flows) {
                burst.recordFlow(session, flow);
            }
            produced.addAll(unexplained.detect(session, flows));
            produced.addAll(burst.detect(session, flows));
            produced.addAll(shulker.detect(session, flows));
            return engine.assess(session.playerId(), produced, Instant.now());
        }

        ItemFlowEvent flow(String material, int amount, FlowSource source, SourceConfidence confidence) {
            return new ItemFlowEvent(
                    UUID.randomUUID(),
                    session.playerId(),
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
}
