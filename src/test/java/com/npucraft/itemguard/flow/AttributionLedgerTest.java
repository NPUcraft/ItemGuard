package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.flow.model.AttributionQuery;
import com.npucraft.itemguard.flow.model.ContainerIdentity;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionLedgerTest {

    @Test
    void sourceHintUsesShortTtl() {
        PluginSettings settings = PluginSettings.defaults();
        assertTrue(settings.sourceHintTtlMillis() <= 500);
        assertTrue(settings.sourceHintTtlMillis() < settings.expectedFlowTtlMillis());
    }

    @Test
    void expiredSourceHintCannotConsumeLaterGain() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(hint(player, "DIAMOND", now.minusMillis(1), FlowSource.CONTAINER));
        assertTrue(ledger.consume(player, "DIAMOND", 64, now).isEmpty());
    }

    @Test
    void sourceHintConsumedOnce() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(hint(player, "DIAMOND", now.plusSeconds(1), FlowSource.CONTAINER));
        assertEquals(32, ledger.consume(player, "DIAMOND", 32, now).getFirst().amount());
        assertTrue(ledger.consume(player, "DIAMOND", 32, now).isEmpty());
    }

    @Test
    void exactCreditSupportsPartialConsumption() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(exact(player, "DIAMOND", 64, now.plusSeconds(3), FlowSource.GROUND_PICKUP));
        assertEquals(10, ledger.consume(player, "DIAMOND", 10, now).getFirst().amount());
        assertEquals(54, ledger.snapshot(player, now).getFirst().remainingAmount());
    }

    @Test
    void guiHintSurvivesUntilPostEventDiff() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant created = Instant.now();
        ledger.add(hint(player, "DIAMOND", created.plusMillis(250), FlowSource.CONTAINER));
        Instant emptyReconcile = created.plusMillis(20);
        assertTrue(ledger.consume(player, "STONE", 1, emptyReconcile).isEmpty());
        assertEquals(1, ledger.hintCount(player, emptyReconcile));
        assertEquals(64, ledger.consume(player, "DIAMOND", 64, created.plusMillis(50)).getFirst().amount());
    }

    @Test
    void oldChestHintDoesNotConsumeGroundPickup() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(exact(player, "DIAMOND", 10, now.plusSeconds(3), FlowSource.GROUND_PICKUP));
        ledger.add(hint(player, "DIAMOND", now.plusMillis(250), FlowSource.CONTAINER));
        List<ExpectedFlowLedger.Consumption> consumed = ledger.consume(player, "DIAMOND", 10, now);
        assertEquals(1, consumed.size());
        assertEquals(FlowSource.GROUND_PICKUP, consumed.getFirst().credit().source());
        assertEquals(1, ledger.hintCount(player, now));
    }

    @Test
    void closestMatchingHintWins() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ContainerIdentity chest = new ContainerIdentity("CHEST", UUID.randomUUID(), "world", 1, 64, 1);
        ContainerIdentity furnace = new ContainerIdentity("FURNACE", UUID.randomUUID(), "world", 2, 64, 2);
        ledger.add(hint(player, "DIAMOND", now.plusMillis(250), FlowSource.CONTAINER, chest, now.minusMillis(200)));
        ledger.add(hint(player, "DIAMOND", now.plusMillis(250), FlowSource.SMELTING, furnace, now.minusMillis(10)));
        var consumed = ledger.consume(player, "DIAMOND", 8, now, new AttributionQuery(furnace, null, FlowSource.SMELTING));
        assertEquals(FlowSource.SMELTING, consumed.getFirst().credit().source());
    }

    @Test
    void baselineResetClearsHintsAndCredits() {
        ExpectedFlowLedger ledger = new ExpectedFlowLedger();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ledger.add(exact(player, "DIAMOND", 64, now.plusSeconds(3), FlowSource.GROUND_PICKUP));
        ledger.add(hint(player, "STONE", now.plusMillis(250), FlowSource.CONTAINER));
        ledger.clear(player);
        assertTrue(ledger.consume(player, "DIAMOND", 64, now).isEmpty());
        assertTrue(ledger.consume(player, "STONE", 8, now).isEmpty());
    }

    private static ExpectedFlowCredit exact(UUID player, String material, int amount, Instant expires, FlowSource source) {
        Instant created = expires.minus(3, java.time.temporal.ChronoUnit.SECONDS);
        return new ExpectedFlowCredit(
                UUID.randomUUID(), player, material, null, amount, source,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.VERIFIED,
                created, expires, UUID.randomUUID(), "test", null, null, "test", false
        );
    }

    private static ExpectedFlowCredit hint(UUID player, String material, Instant expires, FlowSource source) {
        return hint(player, material, expires, source, null, expires.minus(Duration.ofMillis(50)));
    }

    private static ExpectedFlowCredit hint(
            UUID player,
            String material,
            Instant expires,
            FlowSource source,
            ContainerIdentity container,
            Instant created
    ) {
        return new ExpectedFlowCredit(
                UUID.randomUUID(), player, material, null, 10_000, source,
                FlowDestination.PLAYER_INVENTORY, SourceConfidence.VERIFIED,
                created, expires, UUID.randomUUID(), "hint", null, container, "test", true
        );
    }
}
