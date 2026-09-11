package dev.itemguard.log;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileNamesTest {

    @Test
    void dailyAndSizeRotatedNames() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        assertEquals("2026-09-11.jsonl", LogFileNames.fileName(date, 0));
        assertEquals("2026-09-11-1.jsonl", LogFileNames.fileName(date, 1));
        assertEquals("2026-09-11-2.jsonl", LogFileNames.fileName(date, 2));
        assertEquals(date, LogFileNames.dateOf("2026-09-11-2.jsonl"));
        assertEquals(2, LogFileNames.indexOf("2026-09-11-2.jsonl"));
        assertEquals(0, LogFileNames.indexOf("2026-09-11.jsonl"));
    }

    @Test
    void ignoresUnmanagedFiles() {
        assertFalse(LogFileNames.isManagedLog("README.md"));
        assertFalse(LogFileNames.isManagedLog("notes.json"));
        assertFalse(LogFileNames.isManagedLog("../2026-09-11.jsonl"));
        assertTrue(LogFileNames.isManagedLog("2026-09-11.jsonl"));
        assertEquals(List.of(), LogRetention.selectExpired(null, LocalDate.now(), 30));
    }
}
