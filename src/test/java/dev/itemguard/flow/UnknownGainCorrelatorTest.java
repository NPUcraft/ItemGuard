package dev.itemguard.flow;

import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.SourceConfidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownGainCorrelatorTest {

    private final UnknownGainCorrelator correlator = new UnknownGainCorrelator();

    @Test
    void leftoverAfterExpectedCreditIsUnexplained() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, FlowSource.CONTAINER, now));

        UnknownGainCorrelator.CorrelationResult result = correlator.correlate(
                player,
                Map.of("DIAMOND", 128),
                ledger,
                now
        );
        assertEquals(1, result.explained().size());
        assertEquals(64, result.explained().getFirst().amount());
        assertEquals(1, result.unexplained().size());
        assertEquals(64, result.unexplained().getFirst().amount());
        assertEquals("DIAMOND", result.unexplained().getFirst().material());
    }

    @Test
    void fullyExplainedGainHasNoUnknown() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, FlowSource.GROUND_PICKUP, now));
        UnknownGainCorrelator.CorrelationResult result = correlator.correlate(player, Map.of("DIAMOND", 64), ledger, now);
        assertEquals(64, result.explained().getFirst().amount());
        assertTrue(result.unexplained().isEmpty());
        assertEquals(0, result.unexplainedAmount());
    }

    @Test
    void partialChestHintExplainsOnlyActualDelta() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(),
                player,
                "DIAMOND",
                null,
                10_000,
                FlowSource.CONTAINER,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED,
                now,
                now.plusSeconds(3),
                UUID.randomUUID(),
                "shift-click",
                null,
                null,
                "test",
                true
        ));
        UnknownGainCorrelator.CorrelationResult result = correlator.correlate(player, Map.of("DIAMOND", 32), ledger, now);
        assertEquals(32, result.explained().getFirst().amount());
        assertTrue(result.unexplained().isEmpty());
        assertTrue(ledger.snapshot(player, now).isEmpty());
    }

    private static ExpectedFlowCredit credit(UUID player, String material, int amount, FlowSource source, Instant now) {
        return new ExpectedFlowCredit(
                UUID.randomUUID(),
                player,
                material,
                null,
                amount,
                source,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED,
                now,
                now.plusSeconds(3),
                UUID.randomUUID(),
                "test",
                null,
                null,
                "test",
                false
        );
    }
}
