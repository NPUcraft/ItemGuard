package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.UnknownGainSettings;
import com.npucraft.itemguard.item.ItemValueRegistry;

/**
 * Maps unexplained gains onto a value/amount score. UNKNOWN is not itself a cheat conclusion.
 */
public final class UnknownGainScorer {

    private UnknownGainScorer() {
    }

    public static int score(String material, int amount, ItemValueRegistry values, UnknownGainSettings settings) {
        if (amount <= 0 || settings == null) {
            return 0;
        }
        int unit = values == null ? 1 : values.valueOf(material);
        long weighted = (long) unit * Math.max(0, amount);
        if (unit >= settings.extremeValueMin() || weighted >= settings.weightedCritical()) {
            return clamp(settings.extremeScore());
        }
        if (unit >= settings.highValueMin() || weighted >= settings.weightedHigh()) {
            return clamp(settings.highScore());
        }
        if (unit <= settings.lowValueMax()) {
            return clamp(settings.lowScore());
        }
        return clamp(settings.mediumScore());
    }

    private static int clamp(int score) {
        return Math.clamp(score, 0, 100);
    }
}
