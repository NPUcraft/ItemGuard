package com.npucraft.itemguard.api;

import java.util.UUID;

public record AlertView(
        UUID alertId,
        String playerName,
        int riskScore,
        String material,
        int amount,
        String sourceSummary,
        int aggregatedEvents,
        long firstSeenEpochMilli,
        long lastSeenEpochMilli
) {
}
