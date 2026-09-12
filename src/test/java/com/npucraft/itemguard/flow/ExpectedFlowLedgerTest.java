package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedFlowLedgerTest {

    @Test
    void consumesPartiallyAndKeepsRemainder() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, now.plusSeconds(3)));

        List<ExpectedFlowLedger.Consumption> first = ledger.consume(player, "DIAMOND", 32, now);
        assertEquals(1, first.size());
        assertEquals(32, first.getFirst().amount());
        assertEquals(1, ledger.snapshot(player, now).size());
        assertEquals(32, ledger.snapshot(player, now).getFirst().remainingAmount());

        List<ExpectedFlowLedger.Consumption> second = ledger.consume(player, "DIAMOND", 32, now);
        assertEquals(32, second.getFirst().amount());
        assertTrue(ledger.snapshot(player, now).isEmpty());
    }

    @Test
    void expiredCreditsAreNotConsumed() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, now.minusSeconds(1)));
        assertTrue(ledger.consume(player, "DIAMOND", 64, now).isEmpty());
    }

    @Test
    void consumedCreditCannotDoubleCount() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, now.plusSeconds(3)));
        assertEquals(64, ledger.consume(player, "DIAMOND", 64, now).getFirst().amount());
        assertTrue(ledger.consume(player, "DIAMOND", 64, now).isEmpty());
    }

    @Test
    void oneShotHintDiscardsLeftoverAfterPartialConsume() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 10_000, now.plusSeconds(3), true));
        assertEquals(32, ledger.consume(player, "DIAMOND", 32, now).getFirst().amount());
        assertTrue(ledger.snapshot(player, now).isEmpty());
    }

    @Test
    void numericCreditsArePreferredOverOneShotHints() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 10, now.plusSeconds(3), false, FlowSource.GROUND_PICKUP));
        ledger.add(credit(player, "DIAMOND", 10_000, now.plusSeconds(3), true, FlowSource.CONTAINER));
        var consumed = ledger.consume(player, "DIAMOND", 32, now);
        assertEquals(2, consumed.size());
        assertEquals(FlowSource.GROUND_PICKUP, consumed.get(0).credit().source());
        assertEquals(10, consumed.get(0).amount());
        assertEquals(FlowSource.CONTAINER, consumed.get(1).credit().source());
        assertEquals(22, consumed.get(1).amount());
        assertTrue(ledger.snapshot(player, now).isEmpty());
    }

    @Test
    void multipleNumericCreditsSameMaterialAreConsumedInOrder() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 32, now.plusSeconds(3)));
        ledger.add(credit(player, "DIAMOND", 32, now.plusSeconds(3)));
        var consumed = ledger.consume(player, "DIAMOND", 64, now);
        assertEquals(2, consumed.size());
        assertEquals(32, consumed.get(0).amount());
        assertEquals(32, consumed.get(1).amount());
        assertTrue(ledger.snapshot(player, now).isEmpty());
    }

    @Test
    void clearRemovesCreditsSoTheyCannotExplainLaterGains() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(credit(player, "DIAMOND", 64, now.plusSeconds(3)));
        ledger.clear(player);
        assertTrue(ledger.consume(player, "DIAMOND", 64, now).isEmpty());
    }

    private static ExpectedFlowCredit credit(UUID player, String material, int amount, Instant expires) {
        return credit(player, material, amount, expires, false, FlowSource.GROUND_PICKUP);
    }

    private static ExpectedFlowCredit credit(UUID player, String material, int amount, Instant expires, boolean oneShot) {
        return credit(player, material, amount, expires, oneShot, FlowSource.GROUND_PICKUP);
    }

    private static ExpectedFlowCredit credit(UUID player, String material, int amount, Instant expires, boolean oneShot, FlowSource source) {
        Instant created = expires.minus(3, ChronoUnit.SECONDS);
        return new ExpectedFlowCredit(
                UUID.randomUUID(),
                player,
                material,
                null,
                amount,
                source,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED,
                created,
                expires,
                UUID.randomUUID(),
                "test",
                null,
                null,
                "test",
                oneShot
        );
    }
}
