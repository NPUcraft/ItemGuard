package com.npucraft.itemguard.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Lightweight numeric validation. Invalid values warn and fall back; they never crash the server.
 */
public final class ConfigValidator {

    private final List<String> warnings = new ArrayList<>();

    public int positiveInt(String path, int value, int fallback) {
        if (value <= 0) {
            warn(path, value, fallback, "must be > 0");
            return fallback;
        }
        return value;
    }

    public long positiveLong(String path, long value, long fallback) {
        if (value <= 0) {
            warn(path, value, fallback, "must be > 0");
            return fallback;
        }
        return value;
    }

    public int clampInt(String path, int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            warn(path, value, fallback, "must be in " + min + ".." + max);
            return fallback;
        }
        return value;
    }

    public double clampDouble(String path, double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            warn(path, value, fallback, "must be in " + min + ".." + max);
            return fallback;
        }
        return value;
    }

    public boolean bool(String path, Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw == null) {
            warn(path, "null", fallback, "must be true or false");
            return fallback;
        }
        String text = String.valueOf(raw).toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        warn(path, raw, fallback, "must be true or false");
        return fallback;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public void logTo(Logger logger) {
        for (String warning : warnings) {
            logger.warning("[ItemGuard] Invalid config: " + warning);
        }
    }

    private void warn(String path, Object value, Object fallback, String reason) {
        warnings.add(path + " = " + value + " (" + reason + "); using " + fallback);
    }
}
