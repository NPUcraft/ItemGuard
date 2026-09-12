package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.incident.IncidentAlertLevel;
import com.npucraft.itemguard.risk.incident.IncidentEscalation;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskEngineTest {

    private final RiskEngine engine = new RiskEngine(RiskSettings.defaults());

    @Test
    void relatedItemGainSignalsCombineInsideOneIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskAssessment assessment = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "DIAMOND", 64)
        ), now);
        assertEquals(55, assessment.score());
        assertEquals(RiskLevel.SUSPICIOUS, assessment.level());
        assertEquals(1, assessment.activeIncidents().size());
    }

    @Test
    void unrelatedIncidentsDoNotSum() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskAssessment assessment = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "DIAMOND", 64),
                signal(player, SignalType.CONFLICTING_ENCHANTMENTS, 10, now, "NETHERITE_BOOTS", 1, sig("boots"))
        ), now);
        assertEquals(55, assessment.score());
        assertEquals(2, assessment.activeIncidents().size());
    }

    @Test
    void playerRiskIsHighestActiveIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskAssessment assessment = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 55, now, "DIAMOND", 64),
                signal(player, SignalType.CONFLICTING_ENCHANTMENTS, 10, now, "NETHERITE_BOOTS", 1, sig("boots")),
                signal(player, SignalType.SHULKER_HIGH_FLOW, 10, now, "SHULKER_BOX", 64)
        ), now);
        assertEquals(55, assessment.score());
    }

    @Test
    void clampsToOneHundredInsideOneIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        assertEquals(100, engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.RAPID_ITEM_GAIN, 10, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.RARE_ITEM_BURST, 20, now, "NETHERITE_BLOCK", 1728)
        ), now).score());
        assertEquals(0, RiskEngine.clamp(-12));
        assertEquals(100, RiskEngine.clamp(250));
    }

    @Test
    void trustedSourcesDoNotSubtract() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        assertEquals(25, engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 25, now, "DIAMOND", 64),
                signal(player, SignalType.HUSKSYNC_DATA_APPLY, 0, now, "DIAMOND", 64),
                signal(player, SignalType.KNOWN_CONTAINER_SOURCE, 0, now, "DIAMOND", 64)
        ), now).score());
    }

    @Test
    void expiredSignalsAreIgnored() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskSignal expired = new RiskSignal(
                UUID.randomUUID(),
                player,
                SignalType.UNEXPLAINED_ITEM_GAIN,
                35,
                Severity.HIGH,
                now.minusSeconds(10),
                now.minusSeconds(1),
                Map.of(),
                "expired",
                "DIAMOND",
                null,
                64,
                UUID.randomUUID(),
                null
        );
        assertTrue(engine.merge(List.of(expired), List.of(), now).isEmpty());
        assertEquals(0, engine.assess(player, List.of(expired), now).score());
    }

    @Test
    void customMetadataZeroRiskDoesNotCreateIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        assertEquals(0, engine.assess(player, List.of(
                signal(player, SignalType.CUSTOM_ITEM_METADATA, 0, now, "DIAMOND_SWORD", 1),
                signal(player, SignalType.COMPONENT_MODIFIED, 0, now, "DIAMOND_SWORD", 1)
        ), now).score());
        assertTrue(engine.incidents().active(player, now).isEmpty());
    }

    @Test
    void incidentHighAlertOnlyOnThresholdCrossing() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskAssessment first = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "DIAMOND", 64),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, now, "DIAMOND", 64)
        ), now);
        assertEquals(70, first.score());
        assertEquals(1, first.escalations().size());
        assertEquals(IncidentAlertLevel.HIGH, first.escalations().getFirst().to());
        RiskAssessment second = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now.plusSeconds(1), "DIAMOND", 64)
        ), now.plusSeconds(1));
        assertTrue(second.escalations().isEmpty());
        assertEquals(70, second.score());
    }

    @Test
    void incidentCriticalAlertOnlyOnEscalation() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "NETHERITE_BLOCK", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "NETHERITE_BLOCK", 64),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, now, "NETHERITE_BLOCK", 64)
        ), now);
        RiskAssessment critical = engine.assess(player, List.of(
                signal(player, SignalType.RAPID_ITEM_GAIN, 10, now.plusSeconds(1), "NETHERITE_BLOCK", 1728)
        ), now.plusSeconds(1));
        assertEquals(80, critical.score());
        assertEquals(1, critical.escalations().size());
        assertEquals(IncidentAlertLevel.CRITICAL, critical.escalations().getFirst().to());
    }

    @Test
    void criticalIncidentDoesNotAlertOnEveryNewFlow() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, now, "NETHERITE_BLOCK", 1728),
                signal(player, SignalType.RAPID_ITEM_GAIN, 10, now, "NETHERITE_BLOCK", 1728)
        ), now);
        RiskAssessment next = engine.assess(player, List.of(
                signal(player, SignalType.RARE_ITEM_BURST, 20, now.plusSeconds(1), "NETHERITE_BLOCK", 1728)
        ), now.plusSeconds(1));
        assertEquals(100, next.score());
        assertTrue(next.escalations().isEmpty());
    }

    @Test
    void expiredIncidentNoLongerCounts() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        assertEquals(35, engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64)
        ), now).score());
        Instant later = now.plus(RiskSettings.defaults().incidents().itemGain()).plusSeconds(1);
        assertEquals(0, engine.assess(player, List.of(), later).score());
    }

    @Test
    void newIncidentAfterExpiryCanAlertAgain() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, "DIAMOND", 64),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, now, "DIAMOND", 64)
        ), now);
        Instant later = now.plus(RiskSettings.defaults().incidents().itemGain()).plusSeconds(1);
        engine.assess(player, List.of(), later);
        RiskAssessment again = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, later, "DIAMOND", 64),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, later, "DIAMOND", 64),
                signal(player, SignalType.REPEATED_IDENTICAL_ITEMS, 15, later, "DIAMOND", 64)
        ), later);
        assertEquals(1, again.escalations().stream().filter(e -> e.to() == IncidentAlertLevel.HIGH).count());
    }

    @Test
    void relatedSignalsJoinSameItemGainIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        engine.assess(player, List.of(signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "DIAMOND", 64)), now);
        RiskAssessment combined = engine.assess(player, List.of(
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now.plusMillis(50), "DIAMOND", 64)
        ), now.plusMillis(50));
        assertEquals(55, combined.score());
        assertEquals(1, combined.activeIncidents().size());
    }

    @Test
    void laterUnrelatedDiamondWaveDoesNotJoinPriorIncident() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        engine.assess(player, List.of(signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 25, now, "DIAMOND", 64)), now);
        Instant later = now.plusSeconds(5);
        RiskAssessment second = engine.assess(player, List.of(
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, later, "DIAMOND", 64)
        ), later);
        assertEquals(25, second.score());
        assertTrue(second.activeIncidents().size() >= 2);
    }

    @Test
    void zeroAmountStarMaterialIsNotAnEscalationReason() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        List<IncidentEscalation> escalations = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 70, now, "*", 0)
        ), now).escalations();
        assertEquals(1, escalations.size());
        assertEquals("*", escalations.getFirst().material());
        assertEquals(0, escalations.getFirst().amount());
    }

    private static ItemSignature sig(String hash) {
        return new ItemSignature("NETHERITE_BOOTS", hash);
    }

    private static RiskSignal signal(UUID player, SignalType type, int score, Instant now, String material, int amount) {
        return signal(player, type, score, now, material, amount, null);
    }

    private static RiskSignal signal(UUID player, SignalType type, int score, Instant now, String material, int amount, ItemSignature signature) {
        return new RiskSignal(
                UUID.randomUUID(),
                player,
                type,
                score,
                Severity.HIGH,
                now,
                now.plusSeconds(60),
                Map.of("type", type.name(), "source", type == SignalType.UNEXPLAINED_ITEM_GAIN ? "UNKNOWN" : "TEST"),
                type.name(),
                material,
                signature,
                amount,
                UUID.randomUUID(),
                null
        );
    }
}
