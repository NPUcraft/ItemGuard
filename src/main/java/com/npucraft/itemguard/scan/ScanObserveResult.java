package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.risk.model.RiskSignal;

import java.util.List;

/**
 * Scoring signals are ingested by RiskEngine. Logs include NEW/REFRESH/RESOLVED observations.
 */
public record ScanObserveResult(
        List<RiskSignal> signals,
        List<ScannerFindingLog> logs
) {
    public ScanObserveResult {
        signals = signals == null ? List.of() : List.copyOf(signals);
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    public static ScanObserveResult empty() {
        return new ScanObserveResult(List.of(), List.of());
    }
}
