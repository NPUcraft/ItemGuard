package com.npucraft.itemguard.config;

/**
 * Amount/value driven UNKNOWN scoring. UNKNOWN is attribution state, not a cheat conclusion.
 */
public record UnknownGainSettings(
        int lowValueMax,
        int highValueMin,
        int extremeValueMin,
        int lowScore,
        int mediumScore,
        int highScore,
        int extremeScore,
        int weightedHigh,
        int weightedCritical
) {
    public static UnknownGainSettings defaults() {
        return new UnknownGainSettings(
                5,
                20,
                50,
                3,
                10,
                25,
                35,
                800,
                4000
        );
    }
}
