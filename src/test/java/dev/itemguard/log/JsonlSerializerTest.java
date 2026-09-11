package dev.itemguard.log;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlSerializerTest {

    @Test
    void escapesQuotesAndControlCharactersOnOneLine() {
        ForensicLogRecord record = ForensicLogRecord.builder(ForensicLogType.UNKNOWN_GAIN, ForensicLogPriority.NORMAL)
                .instant(Instant.parse("2026-09-11T13:31:42.123Z"))
                .serverName("survival-01")
                .player(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Ste\"ve")
                .material("DIAMOND")
                .amount(64)
                .summary("line1\nline2")
                .metadata(Map.of("note", "a\\b"))
                .build();
        String json = JsonlSerializer.toJson(record, ZoneOffset.UTC);
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\\"));
        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"type\":\"UNKNOWN_GAIN\""));
        assertTrue(json.contains("\"metadata\":{"));
        assertFalse(json.contains("\"metadata\"{"));
        assertTrue(json.contains("2026-09-11T13:31:42.123Z") || json.contains("+00:00"));
    }

    @Test
    void arraysAndMapsIncludeColons() {
        ForensicLogRecord record = ForensicLogRecord.builder(ForensicLogType.INVALID_ITEM, ForensicLogPriority.CRITICAL)
                .signalTypes(List.of("OVER_LEVEL_ENCHANTMENT"))
                .findingTypes(List.of("OVER_LEVEL_ENCHANTMENT"))
                .metadata(Map.of("enchantment", "minecraft:sharpness", "level", "255"))
                .build();
        String json = JsonlSerializer.toJson(record, ZoneOffset.UTC);
        assertTrue(json.contains("\"signalTypes\":[\"OVER_LEVEL_ENCHANTMENT\"]"));
        assertTrue(json.contains("\"findingTypes\":[\"OVER_LEVEL_ENCHANTMENT\"]"));
        assertTrue(json.contains("\"metadata\":{"));
        assertFalse(json.contains("\"signalTypes\"["));
        assertFalse(json.contains("\"findingTypes\"["));
        assertFalse(json.contains("\"metadata\"{"));
    }

    @Test
    void omitsMaterialOnlySignatureAndEmptyCollections() {
        ForensicLogRecord record = ForensicLogRecord.builder(ForensicLogType.HIGH_VALUE_FLOW, ForensicLogPriority.LOW)
                .itemSignature("material-only")
                .signalTypes(List.of())
                .build();
        String json = JsonlSerializer.toJson(record, ZoneOffset.UTC);
        assertFalse(json.contains("itemSignature"));
        assertFalse(json.contains("signalTypes"));
    }

    @Test
    void metadataIsImmutable() {
        ForensicLogRecord record = ForensicLogRecord.builder(ForensicLogType.ALERT, ForensicLogPriority.HIGH)
                .metadata(Map.of("k", "v"))
                .build();
        assertThrows(UnsupportedOperationException.class, () -> record.metadata().put("x", "y"));
        assertEquals("v", record.metadata().get("k"));
    }

    @Test
    void stripsSensitiveMetadataKeys() {
        ForensicLogRecord record = ForensicLogRecord.builder(ForensicLogType.ADMIN_ACTION, ForensicLogPriority.NORMAL)
                .metadata(Map.of(
                        "action", "SCAN",
                        "password", "secret-value",
                        "ip", "127.0.0.1",
                        "nbt", "{too-much}"
                ))
                .build();
        String json = JsonlSerializer.toJson(record, ZoneOffset.UTC);
        assertTrue(json.contains("\"action\":\"SCAN\""));
        assertTrue(json.contains("\"metadata\":{"));
        assertFalse(json.contains("secret-value"));
        assertFalse(json.contains("127.0.0.1"));
        assertFalse(json.contains("{too-much}"));
    }
}
