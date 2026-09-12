package com.npucraft.itemguard.flow;

import java.time.Instant;
import java.util.Map;

/**
 * Compact attribution outcome for debug / forensic context. Never includes inventory contents.
 */
public record AttributionDecision(
        Instant instant,
        String reason,
        String material,
        int actualAmount,
        String selectedSource,
        String selectedKind,
        int pendingHintsCount,
        int expectedCreditsCount,
        String nearestHintSource,
        long nearestHintAgeMs,
        int unmatchedAmount
) {
    public Map<String, String> toMetadata() {
        return Map.of(
                "pendingHintsCount", Integer.toString(pendingHintsCount),
                "expectedCreditsCount", Integer.toString(expectedCreditsCount),
                "nearestHintSource", nearestHintSource == null ? "" : nearestHintSource,
                "nearestHintAgeMs", Long.toString(nearestHintAgeMs),
                "selectedSource", selectedSource == null ? "" : selectedSource,
                "selectedKind", selectedKind == null ? "" : selectedKind,
                "unmatchedAmount", Integer.toString(unmatchedAmount)
        );
    }
}
