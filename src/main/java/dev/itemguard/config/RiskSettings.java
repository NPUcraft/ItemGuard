package dev.itemguard.config;

import dev.itemguard.risk.RiskLevel;
import dev.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public record RiskSettings(
        Map<SignalType, Integer> scores,
        int suspiciousThreshold,
        int highThreshold,
        int criticalThreshold,
        Duration rapidGainWindow,
        Duration highValueBurstWindow,
        Duration repeatedIdenticalWindow,
        Duration shulkerFlowWindow,
        Duration signalTtl,
        int rapidGainAmount,
        int highValueBurstScore,
        int repeatedIdenticalAmount,
        int shulkerHighFlowTransfers,
        long shulkerRapidTransferMillis,
        int unexplainedMinAmount,
        Duration sameSignalCooldown,
        Duration alertCooldown,
        boolean deduplicateSameCorrelation,
        int customMetadataFamilyCap
) {
    public int score(SignalType type) {
        return Math.max(0, scores.getOrDefault(type, 0));
    }

    public RiskLevel levelFor(int score) {
        if (score >= criticalThreshold) {
            return RiskLevel.CRITICAL;
        }
        if (score >= highThreshold) {
            return RiskLevel.HIGH;
        }
        if (score >= suspiciousThreshold) {
            return RiskLevel.SUSPICIOUS;
        }
        return RiskLevel.NORMAL;
    }

    public static RiskSettings defaults() {
        EnumMap<SignalType, Integer> scores = new EnumMap<>(SignalType.class);
        scores.put(SignalType.UNEXPLAINED_ITEM_GAIN, 35);
        scores.put(SignalType.HIGH_VALUE_ITEM_BURST, 20);
        scores.put(SignalType.RARE_ITEM_BURST, 20);
        scores.put(SignalType.REPEATED_IDENTICAL_ITEMS, 15);
        scores.put(SignalType.RAPID_ITEM_GAIN, 10);
        scores.put(SignalType.SHULKER_HIGH_FLOW, 10);
        scores.put(SignalType.SHULKER_RAPID_TRANSFER, 12);
        scores.put(SignalType.ILLEGAL_ENCHANTMENT, 40);
        scores.put(SignalType.OVER_LEVEL_ENCHANTMENT, 40);
        scores.put(SignalType.CONFLICTING_ENCHANTMENTS, 20);
        scores.put(SignalType.OVERSIZED_STACK, 45);
        scores.put(SignalType.SUSPICIOUS_ATTRIBUTE, 15);
        scores.put(SignalType.SUSPICIOUS_COMPONENT, 12);
        scores.put(SignalType.COMPONENT_MODIFIED, 4);
        scores.put(SignalType.CUSTOM_ITEM_METADATA, 2);
        scores.put(SignalType.ABNORMAL_DURABILITY, 25);
        scores.put(SignalType.SUSPICIOUS_CONTAINER_CONTENTS, 18);
        scores.put(SignalType.CUSTOM_MAX_STACK, 8);
        scores.put(SignalType.HUSKSYNC_DATA_APPLY, 0);
        scores.put(SignalType.KNOWN_CONTAINER_SOURCE, 0);
        scores.put(SignalType.HUSKSYNC_REPEATED_SYNC, 8);
        scores.put(SignalType.HUSKSYNC_ROLLBACK_LIKE, 15);
        scores.put(SignalType.HUSKSYNC_RAPID_RESTORE, 12);
        scores.put(SignalType.DUPLICATE_FINGERPRINT, 25);
        return new RiskSettings(
                Map.copyOf(scores),
                30,
                60,
                80,
                Duration.ofMillis(5_000),
                Duration.ofMillis(10_000),
                Duration.ofMillis(30_000),
                Duration.ofMillis(10_000),
                Duration.ofMillis(120_000),
                256,
                400,
                128,
                24,
                750L,
                1,
                Duration.ofMillis(8_000),
                Duration.ofMillis(10_000),
                true,
                10
        );
    }
}
