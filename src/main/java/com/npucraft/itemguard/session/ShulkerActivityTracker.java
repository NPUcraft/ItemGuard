package com.npucraft.itemguard.session;

import java.time.Duration;
import java.time.Instant;

public final class ShulkerActivityTracker {

    private Instant windowStart;
    private int transfers;
    private Instant lastTransfer;
    private Instant previousTransfer;
    private Instant lastOpen;
    private Instant lastClose;

    public void open(Instant now) {
        lastOpen = now;
    }

    public void close(Instant now) {
        lastClose = now;
    }

    public int recordTransfer(Instant now, Duration window) {
        if (windowStart == null || now.isAfter(windowStart.plus(window))) {
            windowStart = now;
            transfers = 0;
        }
        transfers += 1;
        previousTransfer = lastTransfer;
        lastTransfer = now;
        return transfers;
    }

    public boolean isRapid(long rapidMillis) {
        if (previousTransfer == null || lastTransfer == null || transfers <= 1) {
            return false;
        }
        return Duration.between(previousTransfer, lastTransfer).toMillis() <= rapidMillis;
    }

    public int transfers() {
        return transfers;
    }

    public Instant lastOpen() {
        return lastOpen;
    }

    public Instant lastClose() {
        return lastClose;
    }

    public Instant lastTransfer() {
        return lastTransfer;
    }
}
