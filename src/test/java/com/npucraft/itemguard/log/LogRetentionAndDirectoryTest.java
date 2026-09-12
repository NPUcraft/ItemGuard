package com.npucraft.itemguard.log;

import com.npucraft.itemguard.config.LoggingSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRetentionAndDirectoryTest {

    @TempDir
    Path temp;

    @Test
    void selectsExpiredManagedJsonlOnly() throws Exception {
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs.resolve("events"));
        Files.createDirectories(logs.resolve("alerts"));
        Path oldFile = logs.resolve("events").resolve("2026-01-01.jsonl");
        Path keep = logs.resolve("events").resolve("2026-09-01.jsonl");
        Path readme = logs.resolve("events").resolve("README.md");
        Files.writeString(oldFile, "{}\n");
        Files.writeString(keep, "{}\n");
        Files.writeString(readme, "keep me");
        List<Path> expired = LogRetention.selectExpired(logs, LocalDate.of(2026, 9, 11), 30);
        assertEquals(1, expired.size());
        assertTrue(expired.get(0).endsWith("2026-01-01.jsonl"));
        assertEquals(1, LogRetention.delete(expired));
        assertTrue(Files.exists(keep));
        assertTrue(Files.exists(readme));
    }

    @Test
    void pathTraversalFallsBackToLogs() {
        Path data = temp.resolve("ItemGuard").toAbsolutePath();
        Path safe = LogDirectories.resolve(data, "../../somewhere");
        assertEquals(data.resolve("logs").normalize(), safe);
        assertEquals(data.resolve("logs").normalize(), LogDirectories.resolve(data, "C:/windows"));
        assertEquals(data.resolve("logs").normalize(), LogDirectories.resolve(data, "/etc/passwd"));
        Path nested = LogDirectories.resolve(data, "forensics");
        assertEquals(data.resolve("forensics").normalize(), nested);
        assertTrue(nested.startsWith(data.normalize()));
        assertEquals(data.resolve("nested").resolve("ok").normalize(), LogDirectories.resolve(data, "nested/ok"));
    }

    @Test
    void creatingLogDirectoriesDoesNotDeleteExistingUserFile() throws Exception {
        Path logs = temp.resolve("logs");
        Path events = logs.resolve("events");
        Files.createDirectories(events);
        Path userFile = events.resolve("manual-test.jsonl");
        Files.writeString(userFile, "DO_NOT_DELETE");
        LoggingSettings settings = LoggingSettings.defaults(temp);
        JsonlLogWriter writer = new JsonlLogWriter(logs, settings, java.util.logging.Logger.getLogger("test"), java.time.Clock.systemUTC());
        writer.ensureDirectories();
        assertEquals("DO_NOT_DELETE", Files.readString(userFile));
        assertTrue(Files.isDirectory(logs.resolve("alerts")));
        assertTrue(Files.isDirectory(logs.resolve("admin")));
        List<Path> expired = LogRetention.selectExpired(logs, LocalDate.of(2026, 9, 11), 30);
        assertTrue(expired.isEmpty(), "unmanaged user files must not be selected for retention delete");
        writer.close();
        assertEquals("DO_NOT_DELETE", Files.readString(userFile));
    }
}
