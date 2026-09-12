package com.npucraft.itemguard.risk.model;

import com.npucraft.itemguard.risk.RiskLevel;
import com.npucraft.itemguard.risk.incident.IncidentEscalation;
import com.npucraft.itemguard.risk.incident.RiskIncidentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RiskAssessment(
        UUID playerId,
        int score,
        RiskLevel level,
        List<RiskSignal> contributingSignals,
        Instant timestamp,
        UUID highestIncidentId,
        String highestIncidentType,
        List<IncidentEscalation> escalations,
        List<RiskIncidentSnapshot> activeIncidents
) {
    public RiskAssessment {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(level, "level");
        contributingSignals = contributingSignals == null ? List.of() : List.copyOf(contributingSignals);
        Objects.requireNonNull(timestamp, "timestamp");
        escalations = escalations == null ? List.of() : List.copyOf(escalations);
        activeIncidents = activeIncidents == null ? List.of() : List.copyOf(activeIncidents);
        score = Math.clamp(score, 0, 100);
    }

    public static RiskAssessment of(
            UUID playerId,
            int score,
            RiskLevel level,
            List<RiskSignal> contributingSignals,
            Instant timestamp
    ) {
        return new RiskAssessment(playerId, score, level, contributingSignals, timestamp, null, null, List.of(), List.of());
    }
}
