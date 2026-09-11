package dev.itemguard.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertRecord(
        UUID alertId,
        UUID playerId,
        String playerName,
        int riskScore,
        String material,
        int amount,
        String sourceSummary,
        List<String> reasons,
        int aggregatedEvents,
        Instant firstSeen,
        Instant lastSeen,
        UUID correlationId
) {
}
