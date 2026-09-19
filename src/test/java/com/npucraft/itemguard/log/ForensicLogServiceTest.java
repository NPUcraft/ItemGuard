package com.npucraft.itemguard.log;

import com.npucraft.itemguard.config.LoggingSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForensicLogServiceTest {

    @TempDir
    Path temp;

    @Test
    void writesJsonlAndFlushesOnShutdown() throws Exception {
        LoggingSettings settings = settings(temp, 1000, 1024 * 1024, Duration.ofMillis(50));
        ForensicLogService service = new ForensicLogService(temp, settings, Logger.getLogger("test"));
        service.start();
        service.submit(record(ForensicLogType.UNKNOWN_GAIN, ForensicLogPriority.NORMAL, "DIAMOND", 64));
        service.shutdown(Duration.ofSeconds(2));
        Path file = firstJsonl(settings.logsRoot().resolve("events"));
        String line = Files.readString(file, StandardCharsets.UTF_8).trim();
        assertTrue(line.startsWith("{"));
        assertTrue(line.contains("\"type\":\"UNKNOWN_GAIN\""));
        assertTrue(line.contains("\"schemaVersion\":3"));
        assertTrue(line.contains("\"metadata\":{") || !line.contains("\"metadata\""));
        assertFalse(line.contains("\"metadata\"{"));
        assertFalse(line.contains("\n{"));
        assertEquals(1, service.metrics().written());
        assertEquals(0, service.metrics().dropped());
    }

    @Test
    void dropsLowPriorityWhenFullWithoutBlockingOrWriting() throws Exception {
        LoggingSettings settings = settings(temp, 8, 1024 * 1024, Duration.ofSeconds(30));
        ForensicLogService service = new ForensicLogService(temp, settings, Logger.getLogger("test"));
        // Do not start the worker so the queues stay full and the caller never touches disk.
        for (int i = 0; i < 50; i++) {
            service.submit(record(ForensicLogType.HIGH_VALUE_FLOW, ForensicLogPriority.LOW, "COBBLESTONE", i));
        }
        int droppedAfterLow = (int) service.metrics().dropped();
        int sizeAfterLow = service.metrics().queueSize();
        assertTrue(droppedAfterLow > 0);
        service.submit(record(ForensicLogType.INVALID_ITEM, ForensicLogPriority.CRITICAL, "DIAMOND_SWORD", 1));
        assertEquals(droppedAfterLow, (int) service.metrics().dropped());
        assertEquals(0, service.metrics().droppedCritical());
        assertEquals(sizeAfterLow + 1, service.metrics().queueSize());
        assertTrue(jsonlFiles(settings.logsRoot()).isEmpty());
        service.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void thousandRecordsFlushWithoutCallerWrite() throws Exception {
        LoggingSettings settings = settings(temp, 10_000, 10 * 1024 * 1024, Duration.ofMillis(20));
        ForensicLogService service = new ForensicLogService(temp, settings, Logger.getLogger("test"));
        service.start();
        for (int i = 0; i < 1000; i++) {
            service.submit(record(ForensicLogType.UNKNOWN_GAIN, ForensicLogPriority.NORMAL, "DIAMOND", 1));
        }
        service.shutdown(Duration.ofSeconds(5));
        assertEquals(0, service.metrics().dropped());
        assertEquals(1000, service.metrics().written());
        long lines = 0;
        try (Stream<Path> files = Files.list(settings.logsRoot().resolve("events"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".jsonl")).toList()) {
                lines += Files.readAllLines(file).stream().filter(line -> !line.isBlank()).count();
            }
        }
        assertEquals(1000, lines);
    }

    @Test
    void sizeRotationCreatesIndexedFiles() throws Exception {
        LoggingSettings settings = settings(temp, 1000, 400, Duration.ofMillis(10));
        ForensicLogService service = new ForensicLogService(temp, settings, Logger.getLogger("test"));
        service.start();
        for (int i = 0; i < 20; i++) {
            service.submit(record(ForensicLogType.ALERT, ForensicLogPriority.HIGH, "NETHERITE_BLOCK", 64)
            );
        }
        service.shutdown(Duration.ofSeconds(3));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(settings.logsRoot().resolve("alerts"))) {
            stream.filter(path -> path.getFileName().toString().endsWith(".jsonl")).forEach(files::add);
        }
        assertTrue(files.size() >= 2, "expected size rotation, found " + files);
    }

    @Test
    void noopDoesNotWrite() throws Exception {
        NoOpForensicLogSink.INSTANCE.submit(record(ForensicLogType.ALERT, ForensicLogPriority.CRITICAL, "DIAMOND", 1));
        NoOpForensicLogSink.INSTANCE.shutdown(Duration.ofSeconds(1));
        assertFalse(NoOpForensicLogSink.INSTANCE.enabled());
        assertFalse(Files.exists(temp.resolve("logs")));
    }

    @Test
    void rateLimiterSuppressesBursts() {
        ForensicLogService.RateLimiter limiter = new ForensicLogService.RateLimiter();
        assertTrue(limiter.shouldLog(1_000, 2_000));
        assertFalse(limiter.shouldLog(1_000, 2_100));
        assertFalse(limiter.shouldLog(1_000, 2_200));
        assertEquals(3, limiter.take());
        assertTrue(limiter.shouldLog(1_000, 3_200));
    }

    @Test
    void disabledCreateReturnsNoOp() {
        Path logs = temp.resolve("logs");
        LoggingSettings settings = new LoggingSettings(
                false,
                "JSONL",
                "logs",
                logs,
                false,
                30,
                1024 * 1024,
                Duration.ofSeconds(2),
                100,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                30,
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                "test"
        );
        ForensicLogSink sink = ForensicLogService.create(temp, settings, Logger.getLogger("test"));
        assertFalse(sink.enabled());
        sink.submit(record(ForensicLogType.ALERT, ForensicLogPriority.CRITICAL, "DIAMOND", 1));
        assertEquals(0, sink.metrics().written());
        assertTrue(jsonlFiles(logs).isEmpty());
    }

    private static List<Path> jsonlFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".jsonl")).toList();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static LoggingSettings settings(Path dataFolder, int capacity, long maxBytes, Duration flush) {
        Path logs = dataFolder.resolve("logs");
        return new LoggingSettings(
                true,
                "JSONL",
                "logs",
                logs,
                false,
                30,
                maxBytes,
                flush,
                capacity,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                30,
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                "test"
        );
    }

    private static ForensicLogRecord record(ForensicLogType type, ForensicLogPriority priority, String material, int amount) {
        return ForensicLogRecord.builder(type, priority)
                .instant(Instant.parse("2026-09-11T13:00:00Z"))
                .serverName("test")
                .player(UUID.fromString("00000000-0000-0000-0000-000000000042"), "ItemGuardBot")
                .material(material)
                .amount(amount)
                .summary("test-record-" + material + "-" + amount)
                .build();
    }

    private static Path firstJsonl(Path dir) throws Exception {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.toString().endsWith(".jsonl")).findFirst().orElseThrow();
        }
    }
}
