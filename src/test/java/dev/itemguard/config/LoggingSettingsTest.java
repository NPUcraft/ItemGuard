package dev.itemguard.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingSettingsTest {

    @TempDir
    Path temp;

    @Test
    void defaultsMatchReleaseContract() {
        LoggingSettings settings = LoggingSettings.defaults(temp);
        assertTrue(settings.enabled());
        assertEquals("JSONL", settings.format());
        assertEquals("logs", settings.directory());
        assertEquals(temp.resolve("logs").toAbsolutePath().normalize(), settings.logsRoot());
        assertFalse(settings.pathFallback());
        assertEquals(30, settings.retentionDays());
        assertEquals(100L * 1024L * 1024L, settings.maxFileSizeBytes());
        assertEquals(Duration.ofSeconds(2), settings.flushInterval());
        assertEquals(10_000, settings.queueCapacity());
        assertEquals(2_000, settings.urgentCapacity());
        assertEquals(8_000, settings.bulkCapacity());
        assertEquals(30, settings.highValueMinimum());
        assertEquals("unknown", settings.serverName());
        assertTrue(settings.logAlerts());
        assertTrue(settings.logUnknownGains());
        assertTrue(settings.logInvalidItems());
        assertTrue(settings.logSuspiciousItems());
        assertTrue(settings.logHuskSync());
        assertTrue(settings.logAdminActions());
        assertTrue(settings.logHighValueFlows());
    }

    @Test
    void capacityChangeRequiresWriterRestart() {
        LoggingSettings original = LoggingSettings.defaults(temp);
        LoggingSettings resized = new LoggingSettings(
                original.enabled(),
                original.format(),
                original.directory(),
                original.logsRoot(),
                original.pathFallback(),
                original.retentionDays(),
                original.maxFileSizeBytes(),
                original.flushInterval(),
                500,
                original.logAlerts(),
                original.logUnknownGains(),
                original.logInvalidItems(),
                original.logSuspiciousItems(),
                original.logHuskSync(),
                original.logAdminActions(),
                original.logHighValueFlows(),
                original.highValueMinimum(),
                original.dropWarningInterval(),
                original.shutdownTimeout(),
                original.reopenInterval(),
                original.serverName()
        );
        assertTrue(original.needsWriterRestart(resized));
        LoggingSettings sameCapacity = new LoggingSettings(
                original.enabled(),
                original.format(),
                original.directory(),
                original.logsRoot(),
                original.pathFallback(),
                original.retentionDays(),
                original.maxFileSizeBytes(),
                original.flushInterval(),
                original.queueCapacity(),
                false,
                original.logUnknownGains(),
                original.logInvalidItems(),
                original.logSuspiciousItems(),
                original.logHuskSync(),
                original.logAdminActions(),
                original.logHighValueFlows(),
                original.highValueMinimum(),
                original.dropWarningInterval(),
                original.shutdownTimeout(),
                original.reopenInterval(),
                "survival-01"
        );
        assertFalse(original.needsWriterRestart(sameCapacity));
    }
}
