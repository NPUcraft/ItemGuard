package dev.itemguard.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cooldown + short-window aggregation so the same player/material/signal does not spam staff chat.
 * A later critical score still emits even if a lower-risk alert is on cooldown.
 */
public final class AlertAggregator {

    private final Map<AlertKey, Bucket> buckets = new HashMap<>();
    private final Duration cooldown;
    private final Duration window;
    private final int criticalThreshold;

    public AlertAggregator(Duration cooldown, Duration window) {
        this(cooldown, window, 80);
    }

    public AlertAggregator(Duration cooldown, Duration window, int criticalThreshold) {
        this.cooldown = cooldown;
        this.window = window;
        this.criticalThreshold = Math.max(0, criticalThreshold);
    }

    public Optional<AlertRecord> publish(
            AlertKey key,
            String playerName,
            int riskScore,
            int amount,
            String sourceSummary,
            List<String> reasons,
            UUID correlationId,
            Instant now
    ) {
        Bucket bucket = buckets.get(key);
        if (bucket != null && Duration.between(bucket.firstSeen, now).compareTo(window) <= 0) {
            bucket.events += 1;
            bucket.totalAmount += amount;
            bucket.maxRisk = Math.max(bucket.maxRisk, riskScore);
            bucket.lastSeen = now;
            bucket.reasons.addAll(reasons);
            boolean cooling = Duration.between(bucket.emittedAt, now).compareTo(cooldown) < 0;
            boolean escalate = riskScore >= criticalThreshold && bucket.emittedScore < criticalThreshold;
            if (cooling && !escalate) {
                return Optional.empty();
            }
            bucket.emittedAt = now;
            bucket.emittedScore = riskScore;
            return Optional.of(bucket.toRecord(key, playerName, sourceSummary, correlationId));
        }
        Bucket created = new Bucket(now, amount, riskScore, reasons);
        buckets.put(key, created);
        return Optional.of(created.toRecord(key, playerName, sourceSummary, correlationId));
    }

    public void purge(Instant now) {
        buckets.entrySet().removeIf(entry -> Duration.between(entry.getValue().lastSeen, now).compareTo(window.plus(cooldown)) > 0);
    }

    private static final class Bucket {
        private final Instant firstSeen;
        private Instant lastSeen;
        private Instant emittedAt;
        private int events;
        private int totalAmount;
        private int maxRisk;
        private int emittedScore;
        private final List<String> reasons;

        private Bucket(Instant now, int amount, int riskScore, List<String> reasons) {
            this.firstSeen = now;
            this.lastSeen = now;
            this.emittedAt = now;
            this.events = 1;
            this.totalAmount = amount;
            this.maxRisk = riskScore;
            this.emittedScore = riskScore;
            this.reasons = new ArrayList<>(reasons);
        }

        private AlertRecord toRecord(AlertKey key, String playerName, String sourceSummary, UUID correlationId) {
            return new AlertRecord(
                    UUID.randomUUID(),
                    key.playerId(),
                    playerName,
                    maxRisk,
                    key.material(),
                    totalAmount,
                    sourceSummary,
                    List.copyOf(reasons.stream().distinct().toList()),
                    events,
                    firstSeen,
                    lastSeen,
                    correlationId
            );
        }
    }
}
