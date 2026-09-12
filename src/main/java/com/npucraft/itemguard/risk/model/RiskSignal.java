package com.npucraft.itemguard.risk.model;

import com.npucraft.itemguard.item.ItemSignature;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One explainable contribution to a player's risk score.
 * Evidence is both machine-readable ({@link #evidence}) and human-readable ({@link #description}).
 */
public record RiskSignal(
        UUID signalId,
        UUID playerId,
        SignalType type,
        int scoreContribution,
        Severity severity,
        Instant timestamp,
        Instant expiresAt,
        Map<String, String> evidence,
        String description,
        String material,
        ItemSignature signature,
        int amount,
        UUID correlationId,
        UUID flowEventId
) {
    public RiskSignal {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(description, "description");
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        scoreContribution = Math.max(0, scoreContribution);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public String dedupeKey() {
        String mat = material == null ? "*" : material;
        String corr = correlationId == null ? signalId.toString() : correlationId.toString();
        return type + "|" + mat + "|" + corr;
    }
}
