package dev.itemguard.risk;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemGainWindowTest {

    @Test
    void tracksAmountAndValueInsideWindow() {
        ItemGainWindow window = new ItemGainWindow();
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        window.record(now.minusSeconds(20), "DIRT", 64, 1, false, "hash-b", "GROUND_PICKUP");
        window.record(now.minusMillis(3800), "NETHERITE_BLOCK", 1728, 80, false, "hash-a", "SHULKER_BOX");
        assertEquals(1728, window.totalAmount(now, Duration.ofSeconds(5)));
        assertEquals(1728 * 80, window.totalValue(now, Duration.ofSeconds(5)));
        assertEquals(1728, window.signatureTotals(now, Duration.ofSeconds(5)).get("hash-a"));
        window.purge(now, Duration.ofSeconds(10));
        assertTrue(window.inWindow(now, Duration.ofSeconds(30)).stream().noneMatch(gain -> "DIRT".equals(gain.material())));
    }
}
