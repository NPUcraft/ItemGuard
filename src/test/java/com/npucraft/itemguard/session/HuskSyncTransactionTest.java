package com.npucraft.itemguard.session;

import com.npucraft.itemguard.flow.model.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuskSyncTransactionTest {

    @Test
    void completeCycleReturnsToIdle() {
        HuskSyncTransaction transaction = new HuskSyncTransaction();
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        InventorySnapshot before = InventorySnapshot.empty(now);
        UUID id = UUID.randomUUID();
        transaction.begin(before, now, id);
        assertEquals(HuskSyncState.HUSKSYNC_SYNCING, transaction.state());
        assertTrue(transaction.isSyncing());
        assertEquals(id, transaction.transactionId());
        transaction.complete(now.plusSeconds(1));
        assertEquals(HuskSyncState.COMPLETED, transaction.state());
        transaction.resetToIdle();
        assertEquals(HuskSyncState.IDLE, transaction.state());
        assertFalse(transaction.isSyncing());
    }

    @Test
    void timeoutPreventsPermanentSyncing() {
        HuskSyncTransaction transaction = new HuskSyncTransaction();
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        transaction.begin(InventorySnapshot.empty(now), now, UUID.randomUUID());
        assertFalse(transaction.isExpired(now.plusSeconds(5), Duration.ofSeconds(15)));
        assertTrue(transaction.isExpired(now.plusSeconds(16), Duration.ofSeconds(15)));
        transaction.timeout(now.plusSeconds(16));
        assertEquals(HuskSyncState.TIMED_OUT, transaction.state());
        assertFalse(transaction.isSyncing());
    }

    @Test
    void failedSyncLeavesIdlePossibleAfterReset() {
        HuskSyncTransaction transaction = new HuskSyncTransaction();
        Instant now = Instant.now();
        transaction.begin(InventorySnapshot.empty(now), now, UUID.randomUUID());
        transaction.fail("player-quit", now);
        assertEquals(HuskSyncState.FAILED, transaction.state());
        transaction.resetToIdle();
        assertEquals(HuskSyncState.IDLE, transaction.state());
    }

    @Test
    void staleCompleteCannotCloseNewerTransaction() {
        HuskSyncTransaction transaction = new HuskSyncTransaction();
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        transaction.begin(InventorySnapshot.empty(now), now, first);
        transaction.begin(InventorySnapshot.empty(now.plusMillis(5)), now.plusMillis(5), second);
        assertEquals(2, transaction.openCount());
        assertEquals(second, transaction.transactionId());
        assertFalse(transaction.complete(first, now.plusSeconds(1)));
        assertTrue(transaction.isSyncing());
        assertEquals(second, transaction.transactionId());
        assertTrue(transaction.complete(second, now.plusSeconds(2)));
        assertEquals(HuskSyncState.COMPLETED, transaction.state());
    }

    @Test
    void timeoutClearsOpenTransactions() {
        HuskSyncTransaction transaction = new HuskSyncTransaction();
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        transaction.begin(InventorySnapshot.empty(now), now, UUID.randomUUID());
        transaction.timeout(now.plusSeconds(16));
        assertEquals(HuskSyncState.TIMED_OUT, transaction.state());
        assertEquals(0, transaction.openCount());
        transaction.resetToIdle();
        assertEquals(HuskSyncState.IDLE, transaction.state());
        assertFalse(transaction.complete(UUID.randomUUID(), now.plusSeconds(17)));
    }
}
