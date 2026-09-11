package dev.itemguard.session;

import dev.itemguard.flow.model.InventorySnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Tracks one or more overlapping HuskSync apply cycles.
 * HuskSync events do not expose a transaction id, so ItemGuard generates ids internally.
 * A stale complete id cannot close a newer open transaction.
 */
public final class HuskSyncTransaction {

    private final Deque<UUID> openIds = new ArrayDeque<>();
    private HuskSyncState state = HuskSyncState.IDLE;
    private UUID transactionId;
    private InventorySnapshot snapshotBefore;
    private Instant startedAt;
    private Instant completedAt;
    private String note;

    public UUID begin(InventorySnapshot snapshotBefore, Instant now, UUID transactionId) {
        UUID id = transactionId == null ? UUID.randomUUID() : transactionId;
        if (!isSyncing()) {
            this.state = HuskSyncState.HUSKSYNC_SYNCING;
            this.snapshotBefore = snapshotBefore;
            this.startedAt = now;
            this.completedAt = null;
            this.note = "sync-started";
        } else {
            this.note = "overlapping-sync";
        }
        this.transactionId = id;
        openIds.addLast(id);
        return id;
    }

    public boolean complete(UUID id, Instant now) {
        if (!isSyncing()) {
            return false;
        }
        if (id != null && !openIds.contains(id)) {
            return false;
        }
        if (id != null) {
            openIds.remove(id);
        } else if (!openIds.isEmpty()) {
            openIds.removeFirst();
        } else {
            return false;
        }
        if (!openIds.isEmpty()) {
            this.transactionId = openIds.peekLast();
            this.note = "sync-partial-complete";
            return false;
        }
        this.state = HuskSyncState.COMPLETED;
        this.completedAt = now;
        this.note = "sync-complete";
        return true;
    }

    public void complete(Instant now) {
        complete(oldestOpenId(), now);
    }

    public void fail(String reason, Instant now) {
        this.state = HuskSyncState.FAILED;
        this.completedAt = now;
        this.note = reason;
        openIds.clear();
    }

    public void timeout(Instant now) {
        this.state = HuskSyncState.TIMED_OUT;
        this.completedAt = now;
        this.note = "timeout";
        openIds.clear();
    }

    public void resetToIdle() {
        this.state = HuskSyncState.IDLE;
        this.snapshotBefore = null;
        this.startedAt = null;
        this.completedAt = null;
        this.transactionId = null;
        this.note = null;
        openIds.clear();
    }

    public boolean isSyncing() {
        return state == HuskSyncState.HUSKSYNC_SYNCING;
    }

    public boolean isExpired(Instant now, Duration timeout) {
        return isSyncing() && startedAt != null && !startedAt.plus(timeout).isAfter(now);
    }

    public UUID oldestOpenId() {
        return openIds.peekFirst();
    }

    public int openCount() {
        return openIds.size();
    }

    public HuskSyncState state() {
        return state;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public InventorySnapshot snapshotBefore() {
        return snapshotBefore;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String note() {
        return note;
    }
}
