package dev.itemguard.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashingTest {

    @Test
    void sha256IsDeterministic() {
        byte[] payload = "diamond-sword".getBytes(StandardCharsets.UTF_8);
        String first = Hashing.sha256Hex(payload);
        String second = Hashing.sha256Hex(payload);
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertNotEquals(first, Hashing.sha256Hex("diamond-block".getBytes(StandardCharsets.UTF_8)));
    }
}
