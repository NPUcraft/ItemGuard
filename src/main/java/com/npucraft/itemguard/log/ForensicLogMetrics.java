package com.npucraft.itemguard.log;

import java.util.concurrent.atomic.AtomicLong;

public final class ForensicLogMetrics {

    final AtomicLong written = new AtomicLong();
    final AtomicLong dropped = new AtomicLong();
    final AtomicLong droppedCritical = new AtomicLong();
    final AtomicLong lastWriteEpochMillis = new AtomicLong();
    final AtomicLong lastErrorEpochMillis = new AtomicLong();
    volatile int queueSize;
    volatile int queueCapacity;
    volatile String currentFile = "";
    volatile boolean unhealthy;

    public long written() {
        return written.get();
    }

    public long dropped() {
        return dropped.get();
    }

    public long droppedCritical() {
        return droppedCritical.get();
    }

    public int queueSize() {
        return queueSize;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public String currentFile() {
        return currentFile;
    }

    public boolean unhealthy() {
        return unhealthy;
    }

    public String statusLine() {
        return "Queue: " + queueSize + "/" + queueCapacity
                + "  Dropped: " + dropped.get()
                + (currentFile.isBlank() ? "" : "  Current Log: " + currentFile);
    }
}
