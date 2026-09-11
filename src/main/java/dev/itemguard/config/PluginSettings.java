package dev.itemguard.config;

import java.time.Duration;

public record PluginSettings(
        boolean debug,
        long reconcileIntervalTicks,
        long expectedFlowTtlMillis,
        long eventDebounceTicks,
        Duration traceRetention,
        int maxTraceEventsPerPlayer,
        int maxTotalTraceEvents,
        Duration maxTraceQuery,
        int signatureMinValue,
        boolean hashCustomItems,
        long scanDebounceMillis,
        int maxScanDepth,
        Duration huskSyncTimeout,
        boolean keepTraceAfterQuit,
        boolean scannerEnabled,
        boolean scannerAutoRemove,
        boolean ignoreCreativeInventoryGains,
        long heartbeatIntervalTicks
) {
    public static PluginSettings defaults() {
        return new PluginSettings(
                false,
                20L,
                2500L,
                1L,
                Duration.ofMinutes(30),
                2000,
                100_000,
                Duration.ofHours(6),
                20,
                true,
                1500L,
                3,
                Duration.ofMillis(15_000),
                true,
                true,
                false,
                true,
                40L
        );
    }
}
