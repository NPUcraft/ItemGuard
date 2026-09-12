package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanGateTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void adminScanBypassesDebounce() {
        assertTrue(ScanGate.shouldScan(
                true,
                "admin-scan",
                1500,
                now.minusMillis(100),
                now,
                List.of(),
                List.of()
        ));
    }

    @Test
    void joinAndHuskSyncBypassDebounce() {
        Instant recent = now.minusMillis(10);
        assertTrue(ScanGate.shouldScan(true, "join", 1500, recent, now, List.of(), List.of()));
        assertTrue(ScanGate.shouldScan(true, "husksync-complete", 1500, recent, now, List.of(), List.of()));
    }

    @Test
    void unexplainedGainIsDebounced() {
        assertFalse(ScanGate.shouldScan(
                true,
                "inventory-change",
                1500,
                now.minusMillis(100),
                now,
                List.of(unknownGain()),
                List.of()
        ));
    }

    @Test
    void unexplainedGainScansAfterDebounce() {
        assertTrue(ScanGate.shouldScan(
                true,
                "inventory-change",
                1500,
                now.minusMillis(1600),
                now,
                List.of(unknownGain()),
                List.of()
        ));
    }

    @Test
    void disabledScannerNeverRuns() {
        assertFalse(ScanGate.shouldScan(
                false,
                "admin-scan",
                1500,
                now,
                now,
                List.of(unknownGain()),
                List.of(signal())
        ));
    }

    private static ItemFlowEvent unknownGain() {
        return new ItemFlowEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "bot",
                Instant.now(),
                "DIAMOND_SWORD",
                ItemSignature.materialOnly("DIAMOND_SWORD"),
                1,
                FlowSource.UNKNOWN,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.UNKNOWN,
                "world",
                UUID.randomUUID(),
                0,
                100,
                0,
                null,
                "test",
                UUID.randomUUID()
        );
    }

    private static RiskSignal signal() {
        return new RiskSignal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SignalType.UNEXPLAINED_ITEM_GAIN,
                10,
                Severity.HIGH,
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of(),
                "test",
                "DIAMOND",
                ItemSignature.materialOnly("DIAMOND"),
                1,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
