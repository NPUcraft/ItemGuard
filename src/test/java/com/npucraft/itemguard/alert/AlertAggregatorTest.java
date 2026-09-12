package com.npucraft.itemguard.alert;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAggregatorTest {

    @Test
    void aggregatesInsteadOfSpammingDuringCooldown() {
        AlertAggregator aggregator = new AlertAggregator(Duration.ofSeconds(2), Duration.ofSeconds(5), 80);
        AlertKey key = new AlertKey(UUID.randomUUID(), "UNEXPLAINED_ITEM_GAIN", "DIAMOND");
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        Optional<AlertRecord> first = aggregator.publish(key, "Steve", 70, 64, "UNKNOWN", List.of("unexplained"), UUID.randomUUID(), now);
        Optional<AlertRecord> second = aggregator.publish(key, "Steve", 72, 64, "UNKNOWN", List.of("unexplained"), UUID.randomUUID(), now.plusSeconds(1));
        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        AlertRecord later = aggregator.publish(key, "Steve", 75, 64, "UNKNOWN", List.of("burst"), UUID.randomUUID(), now.plusSeconds(3)).orElseThrow();
        assertEquals(192, later.amount());
        assertEquals(3, later.aggregatedEvents());
        assertEquals(75, later.riskScore());
    }

    @Test
    void criticalEscalationBypassesLowerRiskCooldown() {
        AlertAggregator aggregator = new AlertAggregator(Duration.ofSeconds(10), Duration.ofSeconds(20), 80);
        AlertKey key = new AlertKey(UUID.randomUUID(), "UNEXPLAINED_ITEM_GAIN", "DIAMOND");
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        assertTrue(aggregator.publish(key, "Steve", 40, 8, "UNKNOWN", List.of("low"), UUID.randomUUID(), now).isPresent());
        Optional<AlertRecord> critical = aggregator.publish(key, "Steve", 85, 64, "UNKNOWN", List.of("critical"), UUID.randomUUID(), now.plusSeconds(1));
        assertTrue(critical.isPresent());
        assertEquals(85, critical.orElseThrow().riskScore());
    }
}
