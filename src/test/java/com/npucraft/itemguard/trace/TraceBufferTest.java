package com.npucraft.itemguard.trace;

import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceBufferTest {

    @Test
    void dropsOldestWhenOverCapacity() {
        TraceBuffer buffer = new TraceBuffer(3);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        buffer.add(event("DIAMOND", now.minusSeconds(30)));
        buffer.add(event("GOLD_INGOT", now.minusSeconds(20)));
        buffer.add(event("IRON_INGOT", now.minusSeconds(10)));
        buffer.add(event("NETHERITE_BLOCK", now));
        assertEquals(3, buffer.size());
        assertEquals("GOLD_INGOT", buffer.recent(3).getFirst().material());
    }

    @Test
    void purgesExpiredAndFiltersMaterial() {
        TraceBuffer buffer = new TraceBuffer(10);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        buffer.add(event("DIAMOND", now.minusSeconds(120)));
        buffer.add(event("DIAMOND", now.minusSeconds(10)));
        buffer.add(event("APPLE", now.minusSeconds(5)));
        buffer.purgeExpired(now.minusSeconds(60));
        assertEquals(2, buffer.size());
        assertEquals(1, buffer.query(now.minusSeconds(30), now, "DIAMOND").size());
    }

    @Test
    void retainsEventsAfterLogicalOfflineWithoutClearingBuffer() {
        TraceBuffer buffer = new TraceBuffer(10);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        buffer.add(event("DIAMOND", now.minusSeconds(5)));
        assertEquals(1, buffer.size());
        assertEquals("DIAMOND", buffer.query(now.minusSeconds(30), now, "diamond").getFirst().material());
    }

    private static ItemFlowEvent event(String material, Instant timestamp) {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Steve",
                timestamp,
                material,
                ItemSignature.materialOnly(material),
                64,
                FlowSource.GROUND_PICKUP,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED,
                "world",
                UUID.randomUUID(),
                0,
                64,
                0,
                null,
                "test",
                UUID.randomUUID()
        );
    }
}
