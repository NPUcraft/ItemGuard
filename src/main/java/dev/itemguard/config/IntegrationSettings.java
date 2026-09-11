package dev.itemguard.config;

import java.time.Duration;

public record IntegrationSettings(
        boolean huskSyncEnabled,
        boolean huskSyncAttributeAsExpected,
        Duration huskSyncTimeout
) {
    public static IntegrationSettings defaults() {
        return new IntegrationSettings(true, true, Duration.ofMillis(15_000));
    }
}
