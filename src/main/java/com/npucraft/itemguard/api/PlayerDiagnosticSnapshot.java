package com.npucraft.itemguard.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable diagnostics snapshot. Safe to serialize.
 */
public record PlayerDiagnosticSnapshot(
        UUID playerId,
        String playerName,
        boolean online,
        boolean sessionPresent,
        boolean dirty,
        boolean reconcileScheduled,
        long lastReconcileEpochMilli,
        long lastReconcileNanos,
        int riskScore,
        String riskLevel,
        String huskSyncState,
        String huskSyncTransactionId,
        Map<String, Integer> materialTotals,
        Map<String, Integer> nestedTotals,
        Map<String, Integer> lastDiff,
        List<FlowView> recentFlows,
        List<SignalView> recentSignals,
        List<CreditView> expectedCredits,
        List<AlertView> recentAlerts,
        long baselineEpochMilli,
        long huskSyncStartedAtEpochMilli,
        long huskSyncCompletedAtEpochMilli,
        String huskSyncNote,
        int huskSyncOpenCount,
        boolean huskSyncDetected,
        boolean huskSyncActive,
        List<IncidentView> activeIncidents
) {
    public PlayerDiagnosticSnapshot {
        materialTotals = materialTotals == null ? Map.of() : Map.copyOf(materialTotals);
        nestedTotals = nestedTotals == null ? Map.of() : Map.copyOf(nestedTotals);
        lastDiff = lastDiff == null ? Map.of() : Map.copyOf(lastDiff);
        recentFlows = recentFlows == null ? List.of() : List.copyOf(recentFlows);
        recentSignals = recentSignals == null ? List.of() : List.copyOf(recentSignals);
        expectedCredits = expectedCredits == null ? List.of() : List.copyOf(expectedCredits);
        recentAlerts = recentAlerts == null ? List.of() : List.copyOf(recentAlerts);
        activeIncidents = activeIncidents == null ? List.of() : List.copyOf(activeIncidents);
    }
}
