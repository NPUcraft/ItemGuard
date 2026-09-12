package com.npucraft.itemguard.log;

import com.npucraft.itemguard.config.LoggingSettings;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writer-thread-only JSONL appender with daily and size rotation.
 */
public final class JsonlLogWriter implements AutoCloseable {

    private final Path logsRoot;
    private final Logger logger;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<ForensicLogChannel, OpenFile> files = new EnumMap<>(ForensicLogChannel.class);
    private volatile LoggingSettings settings;
    private volatile String lastRelativePath = "";
    private LocalDate lastCleanupDate;
    private boolean unhealthy;
    private long lastReopenAttemptMillis;

    public JsonlLogWriter(Path logsRoot, LoggingSettings settings, Logger logger, Clock clock) {
        this.logsRoot = logsRoot;
        this.settings = settings;
        this.logger = logger;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    public void updateSettings(LoggingSettings settings) {
        this.settings = settings;
    }

    public boolean unhealthy() {
        return unhealthy;
    }

    public String lastRelativePath() {
        return lastRelativePath;
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(logsRoot);
        for (ForensicLogChannel channel : ForensicLogChannel.values()) {
            Files.createDirectories(logsRoot.resolve(channel.directory()));
        }
    }

    public void write(List<ForensicLogRecord> batch) throws IOException {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        cleanupIfNeeded(today);
        boolean critical = false;
        for (ForensicLogRecord record : batch) {
            append(record, today);
            if (record.priority() == ForensicLogPriority.CRITICAL) {
                critical = true;
            }
        }
        if (critical) {
            flush();
        }
    }

    public void flush() throws IOException {
        IOException first = null;
        for (OpenFile file : files.values()) {
            try {
                file.writer.flush();
            } catch (IOException exception) {
                if (first == null) {
                    first = exception;
                }
            }
        }
        if (first != null) {
            markUnhealthy();
            throw first;
        }
    }

    public void maybeReopen() {
        if (!unhealthy) {
            return;
        }
        long now = clock.millis();
        if (now - lastReopenAttemptMillis < settings.reopenInterval().toMillis()) {
            return;
        }
        lastReopenAttemptMillis = now;
        closeQuietly();
        try {
            ensureDirectories();
            unhealthy = false;
        } catch (IOException exception) {
            logger.log(Level.WARNING, "[ItemGuard] Forensic log reopen failed", exception);
        }
    }

    private void append(ForensicLogRecord record, LocalDate today) throws IOException {
        ForensicLogChannel channel = ForensicLogChannel.of(record.type());
        OpenFile file = files.get(channel);
        if (file == null || !file.date.equals(today) || file.bytes >= settings.maxFileSizeBytes()) {
            close(channel);
            file = open(channel, today);
            files.put(channel, file);
        }
        String json = JsonlSerializer.toJson(record, zone);
        byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
        if (file.bytes > 0 && file.bytes + utf8.length + 1 > settings.maxFileSizeBytes()) {
            close(channel);
            file = open(channel, today);
            files.put(channel, file);
        }
        file.writer.write(json);
        file.writer.write('\n');
        file.bytes += utf8.length + 1L;
        lastRelativePath = relative(file.path);
    }

    private OpenFile open(ForensicLogChannel channel, LocalDate today) throws IOException {
        ensureDirectories();
        Path dir = logsRoot.resolve(channel.directory());
        int index = nextIndex(dir, today);
        Path path = dir.resolve(LogFileNames.fileName(today, index));
        boolean exists = Files.exists(path);
        long size = exists ? Files.size(path) : 0L;
        if (exists && size >= settings.maxFileSizeBytes()) {
            index += 1;
            path = dir.resolve(LogFileNames.fileName(today, index));
            exists = Files.exists(path);
            size = exists ? Files.size(path) : 0L;
        }
        Writer writer = new OutputStreamWriter(
                new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)),
                StandardCharsets.UTF_8
        );
        lastRelativePath = relative(path);
        return new OpenFile(path, today, size, writer);
    }

    private int nextIndex(Path dir, LocalDate today) throws IOException {
        int max = 0;
        boolean baseExists = Files.exists(dir.resolve(LogFileNames.fileName(today, 0)));
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.newDirectoryStream(dir, today + "*.jsonl")) {
            for (Path file : stream) {
                int index = LogFileNames.indexOf(file.getFileName().toString());
                if (index > max) {
                    max = index;
                }
                if (index == 0) {
                    baseExists = true;
                }
            }
        }
        Path current = dir.resolve(LogFileNames.fileName(today, max));
        if (Files.exists(current) && Files.size(current) >= settings.maxFileSizeBytes()) {
            return max + 1;
        }
        return baseExists || max > 0 ? max : 0;
    }

    private void cleanupIfNeeded(LocalDate today) {
        if (today.equals(lastCleanupDate)) {
            return;
        }
        lastCleanupDate = today;
        List<Path> expired = LogRetention.selectExpired(logsRoot, today, settings.retentionDays());
        LogRetention.delete(expired);
    }

    public void cleanupNow() {
        cleanupIfNeeded(LocalDate.now(clock));
    }

    private String relative(Path path) {
        Path relative = logsRoot.relativize(path);
        return relative.toString().replace('\\', '/');
    }

    private void markUnhealthy() {
        unhealthy = true;
        lastReopenAttemptMillis = clock.millis();
    }

    private void close(ForensicLogChannel channel) {
        OpenFile file = files.remove(channel);
        if (file != null) {
            try {
                file.writer.flush();
                file.writer.close();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "[ItemGuard] Failed to close forensic log " + file.path, exception);
            }
        }
    }

    private void closeQuietly() {
        for (ForensicLogChannel channel : List.copyOf(files.keySet())) {
            close(channel);
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private static final class OpenFile {
        private final Path path;
        private final LocalDate date;
        private long bytes;
        private final Writer writer;

        private OpenFile(Path path, LocalDate date, long bytes, Writer writer) {
            this.path = path;
            this.date = date;
            this.bytes = bytes;
            this.writer = writer;
        }
    }
}
