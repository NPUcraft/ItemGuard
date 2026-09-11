package dev.itemguard.risk;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.risk.model.RiskAssessment;
import dev.itemguard.risk.model.RiskSignal;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates explainable risk signals into a clamped 0..100 score.
 * Trusted sources contribute 0 rather than negative scores so real anomalies stay visible.
 * <p>
 * Aggregation is a rolling TTL window, not a separate “incident cluster” system: every
 * unexpired signal is summed. Unrelated events inside {@code signal-ttl-millis} can add
 * toward the alert threshold. Same type+material+correlation is deduplicated, and custom
 * metadata signals share a per-correlation family cap so a legal custom item cannot stack
 * those facts past the cap by itself.
 */
public final class RiskEngine {

    private volatile RiskSettings settings;

    public RiskEngine(RiskSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(RiskSettings settings) {
        this.settings = settings;
    }

    public RiskSettings settings() {
        return settings;
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

    public RiskAssessment assess(UUID playerId, List<RiskSignal> signals, Instant now) {
        List<RiskSignal> active = signals.stream().filter(signal -> !signal.isExpired(now)).toList();
        int raw = 0;
        Map<String, Integer> customFamilyUsed = new HashMap<>();
        int familyCap = Math.max(0, settings.customMetadataFamilyCap());
        for (RiskSignal signal : active) {
            int contribution = Math.max(0, signal.scoreContribution());
            if (SignalFamilies.isCustomMetadata(signal.type())) {
                String key = signal.correlationId() == null ? signal.signalId().toString() : signal.correlationId().toString();
                int used = customFamilyUsed.getOrDefault(key, 0);
                int remaining = Math.max(0, familyCap - used);
                contribution = Math.min(contribution, remaining);
                customFamilyUsed.put(key, used + contribution);
            }
            raw += contribution;
        }
        int score = clamp(raw);
        return new RiskAssessment(playerId, score, settings.levelFor(score), List.copyOf(active), now);
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
