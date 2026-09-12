package com.npucraft.itemguard.risk.incident;

import com.npucraft.itemguard.config.IncidentTtlSettings;
import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.RiskLevel;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable live incident. Related evidence of one type can combine; unrelated families cannot.
 */
public final class RiskIncident {

    private final UUID incidentId;
    private final UUID playerId;
    private final IncidentType type;
    private final IncidentKey key;
    private final Instant createdAt;
    private final EnumMap<SignalType, RiskSignal> evidence = new EnumMap<>(SignalType.class);
    private final Set<UUID> correlationIds = new LinkedHashSet<>();

    private Instant lastUpdatedAt;
    private Instant expiresAt;
    private IncidentState state = IncidentState.ACTIVE;
    private IncidentAlertLevel highestAlertedLevel = IncidentAlertLevel.NONE;
    private String material;
    private ItemSignature signature;
    private String sourceContext;
    private int score;
    private String summary = "";

    public RiskIncident(
            UUID playerId,
            IncidentType type,
            IncidentKey key,
            Instant now,
            Duration ttl,
            RiskSignal first
    ) {
        this.incidentId = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.key = key;
        this.createdAt = now;
        this.lastUpdatedAt = now;
        this.expiresAt = now.plus(ttl);
        this.material = first == null ? key.discriminator() : first.material();
        this.signature = first == null ? null : first.signature();
        this.sourceContext = first == null ? null : first.evidence().get("source");
        if (first != null) {
            addEvidence(first, now, ttl, true);
        }
    }

    public UUID incidentId() {
        return incidentId;
    }

    public UUID playerId() {
        return playerId;
    }

    public IncidentType type() {
        return type;
    }

    public IncidentKey key() {
        return key;
    }

    public IncidentState state() {
        return state;
    }

    public IncidentAlertLevel highestAlertedLevel() {
        return highestAlertedLevel;
    }

    public void setHighestAlertedLevel(IncidentAlertLevel level) {
        this.highestAlertedLevel = level;
    }

    public int score() {
        return score;
    }

    public String material() {
        return material == null ? "*" : material;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean isActive(Instant now) {
        return state == IncidentState.ACTIVE && now.isBefore(expiresAt);
    }

    public void expire(Instant now) {
        if (state == IncidentState.ACTIVE) {
            state = IncidentState.EXPIRED;
            expiresAt = now;
        }
    }

    public void resolve(Instant now) {
        state = IncidentState.RESOLVED;
        expiresAt = now;
    }

    public void addEvidence(RiskSignal signal, Instant now, Duration ttl, boolean extendTtl) {
        if (signal == null) {
            return;
        }
        RiskSignal previous = evidence.get(signal.type());
        if (previous == null || signal.scoreContribution() > previous.scoreContribution()) {
            evidence.put(signal.type(), signal);
        }
        if (signal.correlationId() != null) {
            correlationIds.add(signal.correlationId());
        }
        if (signal.material() != null && !signal.material().isBlank() && !"*".equals(signal.material())) {
            this.material = signal.material();
        }
        if (signal.signature() != null && signal.signature().isDetailed()) {
            this.signature = signal.signature();
        }
        if (signal.evidence() != null && signal.evidence().get("source") != null) {
            this.sourceContext = signal.evidence().get("source");
        }
        lastUpdatedAt = now;
        if (extendTtl || previous == null) {
            expiresAt = now.plus(ttl);
        } else if (type == IncidentType.ILLEGAL_ITEM || type == IncidentType.SUSPICIOUS_ITEM) {
            expiresAt = now.plus(ttl);
        }
        recalc();
    }

    public List<RiskSignal> evidence() {
        return List.copyOf(evidence.values());
    }

    public UUID primaryCorrelation() {
        return correlationIds.isEmpty() ? null : correlationIds.iterator().next();
    }

    public RiskIncidentSnapshot snapshot(RiskSettings settings) {
        return new RiskIncidentSnapshot(
                incidentId,
                playerId,
                type,
                state,
                material(),
                signature == null ? null : signature.hash(),
                score,
                settings.levelFor(score),
                highestAlertedLevel,
                createdAt,
                lastUpdatedAt,
                expiresAt,
                summary,
                evidence()
        );
    }

    public static Duration ttlFor(IncidentType type, IncidentTtlSettings settings) {
        return switch (type) {
            case ITEM_GAIN -> settings.itemGain();
            case ILLEGAL_ITEM -> settings.illegalItem();
            case SUSPICIOUS_ITEM -> settings.suspiciousItem();
            case SHULKER_ACTIVITY -> settings.shulkerActivity();
        };
    }

    private void recalc() {
        int raw = 0;
        List<String> parts = new ArrayList<>();
        for (RiskSignal signal : evidence.values()) {
            raw += Math.max(0, signal.scoreContribution());
            parts.add(signal.type().name() + " +" + signal.scoreContribution());
        }
        this.score = Math.clamp(raw, 0, 100);
        this.summary = String.join(", ", parts);
    }
}
