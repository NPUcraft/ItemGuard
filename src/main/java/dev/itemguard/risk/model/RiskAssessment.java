package dev.itemguard.risk.model;

import dev.itemguard.risk.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RiskAssessment(
        UUID playerId,
        int score,
        RiskLevel level,
        List<RiskSignal> contributingSignals,
        Instant timestamp
) {
    public RiskAssessment {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(level, "level");
        contributingSignals = List.copyOf(contributingSignals);
        Objects.requireNonNull(timestamp, "timestamp");
        score = Math.clamp(score, 0, 100);
    }
}
