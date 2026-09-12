package com.npucraft.itemguard.config;

import java.time.Duration;

public record IncidentTtlSettings(
        Duration itemGain,
        Duration illegalItem,
        Duration suspiciousItem,
        Duration shulkerActivity,
        int maxActivePerPlayer,
        Duration itemGainJoin
) {
    public static IncidentTtlSettings defaults() {
        return new IncidentTtlSettings(
                Duration.ofSeconds(20),
                Duration.ofSeconds(120),
                Duration.ofSeconds(120),
                Duration.ofSeconds(15),
                16,
                Duration.ofMillis(2_000)
        );
    }
}
