package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.risk.RiskSignals;
import com.npucraft.itemguard.risk.incident.PlayerIncidentTracker;
import com.npucraft.itemguard.risk.model.RiskSignal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts scanner findings into at most one risk contribution per player + signature + finding type.
 * Paper-free so unit tests can exercise dedupe without loading data-component types.
 */
public final class ScannerFindingObserver {

    private final ScannerFindingTracker findings = new ScannerFindingTracker();
    private volatile RiskSettings riskSettings;

    public ScannerFindingObserver(RiskSettings riskSettings) {
        this.riskSettings = riskSettings;
    }

    public void updateSettings(RiskSettings riskSettings) {
        this.riskSettings = riskSettings;
    }

    public ScannerFindingTracker findings() {
        return findings;
    }

    public void clearPlayer(UUID playerId) {
        findings.clear(playerId);
    }

    public List<RiskSignal> observe(
            UUID playerId,
            List<ScanFinding> found,
            UUID correlationId,
            Instant now,
            PlayerIncidentTracker incidents
    ) {
        return observeDetailed(playerId, found, correlationId, now, incidents).signals();
    }

    public ScanObserveResult observeDetailed(
            UUID playerId,
            List<ScanFinding> found,
            UUID correlationId,
            Instant now,
            PlayerIncidentTracker incidents
    ) {
        findings.beginScan(playerId);
        List<RiskSignal> signals = new ArrayList<>();
        List<ScannerFindingLog> logs = new ArrayList<>();
        int slot = 0;
        for (ScanFinding finding : found) {
            ScannerFindingTracker.ObserveResult observed = findings.observe(playerId, finding, slot++, now);
            if (observed.state() == null) {
                continue;
            }
            boolean score = observed.newRiskEvidence()
                    && finding.classification() != ScanClassification.INFO
                    && finding.classification() != ScanClassification.CUSTOM
                    && (riskSettings.score(finding.signalType()) > 0
                    || finding.classification() == ScanClassification.INVALID);
            int applied = score ? riskSettings.score(finding.signalType()) : 0;
            if (finding.classification() == ScanClassification.INVALID && score) {
                applied = Math.max(applied, riskSettings.score(finding.signalType()));
            }
            logs.add(toLog(playerId, finding, observed, correlationId, applied));
            if (score) {
                signals.add(toSignal(playerId, finding, correlationId));
            }
        }
        for (ScannerFindingTracker.FindingState resolved : findings.finishScan(playerId, now, incidents)) {
            logs.add(new ScannerFindingLog(
                    now,
                    playerId,
                    correlationId,
                    resolved.key(),
                    FindingLifecycle.RESOLVED,
                    resolved.ruleId(),
                    resolved.triggerReason().isBlank() ? "resolved" : resolved.triggerReason(),
                    resolved.material(),
                    0,
                    resolved.type(),
                    ScanClassification.INFO,
                    com.npucraft.itemguard.risk.model.Severity.INFO,
                    0,
                    null
            ));
        }
        return new ScanObserveResult(signals, logs);
    }

    private static ScannerFindingLog toLog(
            UUID playerId,
            ScanFinding finding,
            ScannerFindingTracker.ObserveResult observed,
            UUID correlationId,
            int riskApplied
    ) {
        return new ScannerFindingLog(
                observed.state().lastSeen(),
                playerId,
                correlationId,
                observed.state().key(),
                observed.lifecycle(),
                finding.ruleId(),
                ScannerFindingLog.boundedReason(finding.description()),
                finding.material(),
                finding.amount(),
                finding.signalType(),
                finding.classification(),
                finding.severity(),
                riskApplied,
                finding.signature()
        );
    }

    private RiskSignal toSignal(UUID playerId, ScanFinding finding, UUID correlationId) {
        return RiskSignals.create(
                playerId,
                finding.signalType(),
                riskSettings,
                finding.severity(),
                finding.material(),
                finding.signature(),
                finding.amount(),
                correlationId,
                null,
                finding.description(),
                finding.evidence()
        );
    }
}
