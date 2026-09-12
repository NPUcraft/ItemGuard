package com.npucraft.itemguard.risk.incident;

import com.npucraft.itemguard.risk.RiskLevel;
import com.npucraft.itemguard.risk.model.RiskSignal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskIncidentSnapshot(
        UUID incidentId,
        UUID playerId,
        IncidentType type,
        IncidentState state,
        String material,
        String signatureHash,
        int score,
        RiskLevel level,
        IncidentAlertLevel highestAlertedLevel,
        Instant createdAt,
        Instant lastUpdatedAt,
        Instant expiresAt,
        String summary,
        List<RiskSignal> evidence
) {
    public RiskIncidentSnapshot {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        material = material == null ? "*" : material;
    }

    public String shortId() {
        String raw = incidentId.toString().replace("-", "");
        return raw.substring(0, Math.min(8, raw.length())).toUpperCase();
    }
}
