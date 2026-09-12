package com.npucraft.itemguard.config;

/**
 * Semantic gates so bulk dirt/stone cannot be called a high-value or rare-identical event.
 */
public record DetectorGateSettings(
        int highValueBurstMinimumItemValue,
        int repeatedIdenticalMinimumItemValue,
        int rapidLowValueMaxItemValue,
        int rapidLowValueScore,
        int shulkerGateMinimumItemValue
) {
    public static DetectorGateSettings defaults() {
        return new DetectorGateSettings(20, 20, 5, 3, 20);
    }
}
