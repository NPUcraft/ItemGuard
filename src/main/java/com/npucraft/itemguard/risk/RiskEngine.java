package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.risk.incident.PlayerIncidentTracker;
import com.npucraft.itemguard.risk.incident.RiskIncident;
import com.npucraft.itemguard.risk.incident.RiskIncidentSnapshot;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Incident-based risk. Player current risk is {@code MAX(active incident scores)},
 * not the sum of every unexpired signal on the player.
 */
public final class RiskEngine {

    private volatile RiskSettings settings;
    private final PlayerIncidentTracker incidents = new PlayerIncidentTracker();

    public RiskEngine(RiskSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(RiskSettings settings) {
        this.settings = settings;
    }

    public RiskSettings settings() {
        return settings;
    }

    public PlayerIncidentTracker incidents() {
        return incidents;
    }

    public List<RiskSignal> merge(List<RiskSignal> existing, List<RiskSignal> incoming, Instant now) {
        Map<String, RiskSignal> merged = new LinkedHashMap<>();
        for (RiskSignal signal : existing) {
            if (!signal.isExpired(now)) {
                merged.put(dedupeKey(signal), signal);
            }
        }
        for (RiskSignal signal : incoming) {
            if (signal.isExpired(now)) {
                continue;
            }
            String key = dedupeKey(signal);
            RiskSignal previous = merged.get(key);
            if (previous == null || signal.scoreContribution() > previous.scoreContribution()) {
                merged.put(key, signal);
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * Ingest newly produced signals into incidents and return the player snapshot.
     * Historical session signals must not be re-ingested here or TTLs become sticky.
     */
    public RiskAssessment assess(UUID playerId, List<RiskSignal> produced, Instant now) {
        var escalations = incidents.ingest(playerId, produced, now, settings);
        incidents.purge(playerId, now);
        int score = incidents.currentRisk(playerId, now);
        List<RiskSignal> evidence = incidents.activeEvidence(playerId, now);
        RiskIncident highest = incidents.highest(playerId, now);
        List<RiskIncidentSnapshot> snapshots = incidents.snapshots(playerId, now, settings);
        return new RiskAssessment(
                playerId,
                score,
                settings.levelFor(score),
                evidence,
                now,
                highest == null ? null : highest.incidentId(),
                highest == null ? null : highest.type().name(),
                escalations,
                snapshots
        );
    }

    public void clearPlayer(UUID playerId) {
        incidents.clear(playerId);
    }

    public void clearAll() {
        incidents.clearAll();
    }

    public static int clamp(int score) {
        return Math.clamp(score, 0, 100);
    }

    private String dedupeKey(RiskSignal signal) {
        if (settings.deduplicateSameCorrelation()) {
            return signal.dedupeKey();
        }
        return signal.signalId().toString();
    }
}
