package dev.itemguard.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesCommonUnits() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s").orElseThrow());
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m").orElseThrow());
        assertEquals(Duration.ofHours(1), DurationParser.parse("1h").orElseThrow());
        assertEquals(Duration.ofSeconds(12), DurationParser.parse("12").orElseThrow());
    }

    @Test
    void rejectsInvalidInput() {
        assertTrue(DurationParser.parse("abc").isEmpty());
        assertTrue(DurationParser.parse("0s").isEmpty());
        assertTrue(DurationParser.parse("").isEmpty());
    }

    @Test
    void formatsRoundTripFriendlyValues() {
        assertEquals("5m", DurationParser.format(Duration.ofMinutes(5)));
        assertEquals("30s", DurationParser.format(Duration.ofSeconds(30)));
        assertEquals("1h", DurationParser.format(Duration.ofHours(1)));
    }
}
