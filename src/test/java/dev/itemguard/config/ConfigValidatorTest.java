package dev.itemguard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    @Test
    void fallsBackOnInvalidValues() {
        ConfigValidator validator = new ConfigValidator();
        assertEquals(20, validator.positiveInt("interval", 0, 20));
        assertEquals(60, validator.clampInt("threshold", 180, 0, 100, 60));
        assertEquals(true, validator.bool("debug", "nope", true));
        assertTrue(validator.hasWarnings());
        assertEquals(3, validator.warnings().size());
    }
}
