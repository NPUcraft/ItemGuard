package dev.itemguard.util;

/**
 * Main-thread light stats. No external metrics library.
 */
public final class PerformanceStats {

    private long lastReconcileNanos;
    private long lastScanNanos;
    private long reconcileCount;
    private long totalReconcileNanos;
    private int lastDirtyQueue;

    public void recordReconcile(long nanos) {
        lastReconcileNanos = nanos;
        reconcileCount += 1;
        totalReconcileNanos += nanos;
    }

    public void recordScan(long nanos) {
        lastScanNanos = nanos;
    }

    public void setDirtyQueue(int size) {
        lastDirtyQueue = size;
    }

    public long lastReconcileMillis() {
        return lastReconcileNanos / 1_000_000L;
    }

    public long averageReconcileMillis() {
        if (reconcileCount == 0) {
            return 0;
        }
        return (totalReconcileNanos / reconcileCount) / 1_000_000L;
    }

    public long lastScanMillis() {
        return lastScanNanos / 1_000_000L;
    }

    public long reconcileCount() {
        return reconcileCount;
    }

    public int lastDirtyQueue() {
        return lastDirtyQueue;
    }
}
