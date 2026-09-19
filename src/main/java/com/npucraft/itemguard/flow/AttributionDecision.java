package com.npucraft.itemguard.flow;

import java.time.Instant;
import java.util.LinkedHashMap;
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
        int unmatchedAmount,
        int matchingHintCount,
        int consumedCount
) {
    public Map<String, String> toMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pendingHintsCount", Integer.toString(pendingHintsCount));
        metadata.put("expectedCreditsCount", Integer.toString(expectedCreditsCount));
        metadata.put("matchingHintCount", Integer.toString(matchingHintCount));
        metadata.put("consumedCount", Integer.toString(consumedCount));
        metadata.put("nearestHintSource", nearestHintSource == null ? "" : nearestHintSource);
        metadata.put("nearestHintAgeMs", Long.toString(nearestHintAgeMs));
        metadata.put("selectedSource", selectedSource == null ? "" : selectedSource);
        metadata.put("selectedKind", selectedKind == null ? "" : selectedKind);
        metadata.put("unmatchedAmount", Integer.toString(unmatchedAmount));
        metadata.put("diagnosisStage", unmatchedAmount > 0 ? "pre-consume" : "post-consume");
        return Map.copyOf(metadata);
    }
}
