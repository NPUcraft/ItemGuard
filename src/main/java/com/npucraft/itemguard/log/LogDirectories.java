package com.npucraft.itemguard.log;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves the forensic log root under the plugin data folder. Rejects path traversal.
 */
public final class LogDirectories {

    private LogDirectories() {
    }

    public static Path resolve(Path dataFolder, String configured) {
        Path base = dataFolder.toAbsolutePath().normalize();
        String raw = configured == null || configured.isBlank() ? "logs" : configured.trim();
        if (!safeRelative(raw)) {
            return base.resolve("logs").normalize();
        }
        Path resolved = base.resolve(raw).normalize();
        if (!resolved.startsWith(base)) {
            return base.resolve("logs").normalize();
        }
        return resolved;
    }

    public static boolean safeRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.contains("..")) {
            return false;
        }
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            return false;
        }
        if (trimmed.length() >= 2 && trimmed.charAt(1) == ':') {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !lower.startsWith("file:");
    }
}
