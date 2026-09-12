package com.npucraft.itemguard.alert;

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
        UUID correlationId,
        UUID incidentId,
        String incidentType,
        String alertTransition
) {
    public AlertRecord {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        material = material == null ? "*" : material;
    }

    public AlertRecord(
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
        this(alertId, playerId, playerName, riskScore, material, amount, sourceSummary, reasons, aggregatedEvents, firstSeen, lastSeen, correlationId, null, null, null);
    }
}
