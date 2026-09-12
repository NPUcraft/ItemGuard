package com.npucraft.itemguard.log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes only ItemGuard-managed JSONL files older than the retention window.
 */
public final class LogRetention {

    private LogRetention() {
    }

    public static List<Path> selectExpired(Path logsRoot, LocalDate today, int retentionDays) {
        List<Path> expired = new ArrayList<>();
        if (logsRoot == null || retentionDays <= 0) {
            return expired;
        }
        LocalDate cutoff = today.minusDays(retentionDays);
        for (ForensicLogChannel channel : ForensicLogChannel.values()) {
            Path dir = logsRoot.resolve(channel.directory());
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jsonl")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    if (!LogFileNames.isManagedLog(name)) {
                        continue;
                    }
                    LocalDate date = LogFileNames.dateOf(name);
                    if (date != null && date.isBefore(cutoff)) {
                        expired.add(file);
                    }
                }
            } catch (IOException ignored) {
                // Writer thread logs this at the call site.
            }
        }
        return List.copyOf(expired);
    }

    public static int delete(List<Path> files) {
        int deleted = 0;
        for (Path file : files) {
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException ignored) {
                // best-effort
            }
        }
        return deleted;
    }
}
