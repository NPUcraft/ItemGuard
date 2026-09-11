package dev.itemguard.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSignatureTest {

    @Test
    void amountIsNotPartOfIdentity() {
        ItemSignature one = new ItemSignature("DIAMOND_SWORD", "abc123");
        ItemSignature sixtyFour = new ItemSignature("DIAMOND_SWORD", "abc123");
        assertEquals(one, sixtyFour);
        assertNotEquals(one, new ItemSignature("DIAMOND_SWORD", "other"));
        assertTrue(one.isDetailed());
        assertEquals("DIAMOND", ItemSignature.materialOnly("DIAMOND").material());
    }
}
