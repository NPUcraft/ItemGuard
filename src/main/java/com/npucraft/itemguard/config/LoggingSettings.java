package com.npucraft.itemguard.config;

import com.npucraft.itemguard.log.LogDirectories;

import java.nio.file.Path;
import java.time.Duration;

public record LoggingSettings(
        boolean enabled,
        String format,
        String directory,
        Path logsRoot,
        boolean pathFallback,
        int retentionDays,
        long maxFileSizeBytes,
        Duration flushInterval,
        int queueCapacity,
        boolean logAlerts,
        boolean logUnknownGains,
        boolean logInvalidItems,
        boolean logSuspiciousItems,
        boolean logHuskSync,
        boolean logAdminActions,
        boolean logHighValueFlows,
        int highValueMinimum,
        Duration dropWarningInterval,
        Duration shutdownTimeout,
        Duration reopenInterval,
        String serverName
) {
    public static LoggingSettings defaults(Path dataFolder) {
        Path root = LogDirectories.resolve(dataFolder, "logs");
        return new LoggingSettings(
                true,
                "JSONL",
                "logs",
                root,
                false,
                30,
                100L * 1024L * 1024L,
                Duration.ofSeconds(2),
                10_000,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                30,
                Duration.ofSeconds(60),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                "unknown"
        );
    }

    public int urgentCapacity() {
        return Math.max(1, queueCapacity / 5);
    }

    public int bulkCapacity() {
        return Math.max(1, queueCapacity - urgentCapacity());
    }

    public boolean needsWriterRestart(LoggingSettings other) {
        if (other == null) {
            return true;
        }
        return enabled != other.enabled
                || !logsRoot.equals(other.logsRoot)
                || queueCapacity != other.queueCapacity
                || maxFileSizeBytes != other.maxFileSizeBytes
                || !flushInterval.equals(other.flushInterval)
                || retentionDays != other.retentionDays
                || !format.equalsIgnoreCase(other.format);
    }
}
