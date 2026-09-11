package dev.itemguard.api;

import java.util.UUID;

public record SignalView(
        UUID signalId,
        String type,
        int score,
        String severity,
        String material,
        String description,
        long timestampEpochMilli,
        long expiresAtEpochMilli,
        boolean expired
) {
}
