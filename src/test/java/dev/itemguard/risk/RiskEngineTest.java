package dev.itemguard.risk;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.risk.model.RiskAssessment;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
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
    void aggregatesPositiveScores() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        List<RiskSignal> signals = List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID()),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, UUID.randomUUID())
        );
        RiskAssessment assessment = engine.assess(player, signals, now);
        assertEquals(55, assessment.score());
        assertEquals(RiskLevel.SUSPICIOUS, assessment.level());
    }

    @Test
    void clampsToOneHundred() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        List<RiskSignal> signals = List.of(
                signal(player, SignalType.OVERSIZED_STACK, 45, now, UUID.randomUUID()),
                signal(player, SignalType.OVER_LEVEL_ENCHANTMENT, 40, now, UUID.randomUUID()),
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID())
        );
        assertEquals(100, engine.assess(player, signals, now).score());
        assertEquals(0, RiskEngine.clamp(-12));
        assertEquals(100, RiskEngine.clamp(250));
    }

    @Test
    void trustedSourcesDoNotSubtract() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        List<RiskSignal> signals = List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID()),
                signal(player, SignalType.HUSKSYNC_DATA_APPLY, 0, now, UUID.randomUUID()),
                signal(player, SignalType.KNOWN_CONTAINER_SOURCE, 0, now, UUID.randomUUID())
        );
        assertEquals(35, engine.assess(player, signals, now).score());
    }

    @Test
    void deduplicatesSameCorrelationAndType() {
        UUID player = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        Instant now = Instant.now();
        RiskSignal first = signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, correlation);
        RiskSignal duplicate = signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, correlation);
        List<RiskSignal> merged = engine.merge(List.of(first), List.of(duplicate), now);
        assertEquals(1, merged.size());
        assertEquals(35, engine.assess(player, merged, now).score());
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
    void unrelatedSignalsInsideTtlAreAdditive() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        List<RiskSignal> belowAlert = List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID()),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, UUID.randomUUID())
        );
        assertEquals(55, engine.assess(player, belowAlert, now).score());
        List<RiskSignal> withShulker = List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID()),
                signal(player, SignalType.HIGH_VALUE_ITEM_BURST, 20, now, UUID.randomUUID()),
                signal(player, SignalType.SHULKER_HIGH_FLOW, 10, now, UUID.randomUUID())
        );
        assertEquals(65, engine.assess(player, withShulker, now).score());
    }

    @Test
    void customMetadataFamilyIsCappedPerCorrelation() {
        UUID player = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        Instant now = Instant.now();
        List<RiskSignal> signals = List.of(
                signal(player, SignalType.CUSTOM_ITEM_METADATA, 2, now, correlation),
                signal(player, SignalType.CUSTOM_ITEM_METADATA, 2, now, correlation),
                signal(player, SignalType.CUSTOM_ITEM_METADATA, 2, now, correlation),
                signal(player, SignalType.COMPONENT_MODIFIED, 4, now, correlation),
                signal(player, SignalType.CUSTOM_MAX_STACK, 8, now, correlation),
                signal(player, SignalType.SUSPICIOUS_COMPONENT, 12, now, correlation)
        );
        assertEquals(10, engine.assess(player, signals, now).score());
    }

    @Test
    void customMetadataCapDoesNotHideUnexplainedGain() {
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        UUID custom = UUID.randomUUID();
        List<RiskSignal> signals = List.of(
                signal(player, SignalType.CUSTOM_ITEM_METADATA, 2, now, custom),
                signal(player, SignalType.COMPONENT_MODIFIED, 4, now, custom),
                signal(player, SignalType.CUSTOM_MAX_STACK, 8, now, custom),
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, UUID.randomUUID())
        );
        assertEquals(45, engine.assess(player, signals, now).score());
    }

    private static RiskSignal signal(UUID player, SignalType type, int score, Instant now, UUID correlation) {
        return new RiskSignal(
                UUID.randomUUID(),
                player,
                type,
                score,
                Severity.HIGH,
                now,
                now.plusSeconds(60),
                Map.of("type", type.name()),
                type.name(),
                "DIAMOND",
                null,
                64,
                correlation,
                null
        );
    }
}
