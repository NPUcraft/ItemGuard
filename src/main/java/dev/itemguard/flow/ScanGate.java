package dev.itemguard.flow;

import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.model.RiskSignal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a reconcile pass should run the illegal-item scanner.
 * Forced reasons bypass debounce so {@code /ig scan} and join/HuskSync actually update session signals.
 */
public final class ScanGate {

    private static final Set<String> FORCE_SCAN = Set.of("admin-scan", "join", "husksync-complete");

    private ScanGate() {
    }

    public static boolean shouldScan(
            boolean scannerEnabled,
            String reason,
            long debounceMillis,
            Instant lastScanTime,
            Instant now,
            List<ItemFlowEvent> flows,
            List<RiskSignal> detectorSignals
    ) {
        if (!scannerEnabled) {
            return false;
        }
        if (FORCE_SCAN.contains(reason)) {
            return true;
        }
        if (lastScanTime != null
                && now.toEpochMilli() - lastScanTime.toEpochMilli() < debounceMillis) {
            return false;
        }
        boolean unexplained = flows.stream().anyMatch(ItemFlowEvent::isUnexplained);
        boolean interesting = unexplained || flows.stream().anyMatch(ItemFlowEvent::isGain);
        boolean risky = detectorSignals.stream().anyMatch(signal -> signal.scoreContribution() > 0);
        return interesting && risky || unexplained;
    }
}
