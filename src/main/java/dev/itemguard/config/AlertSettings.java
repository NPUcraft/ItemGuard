package dev.itemguard.config;

import java.time.Duration;

public record AlertSettings(
        boolean enabled,
        int threshold,
        int criticalThreshold,
        boolean console,
        boolean adminChat,
        String permission,
        boolean aggregationEnabled,
        Duration aggregationWindow,
        boolean inspectButton,
        boolean traceButton,
        boolean teleportButton,
        String traceDuration
) {
    public static AlertSettings defaults() {
        return new AlertSettings(
                true,
                60,
                80,
                true,
                true,
                "itemguard.alerts",
                true,
                Duration.ofMillis(5_000),
                true,
                true,
                true,
                "5m"
        );
    }
}
