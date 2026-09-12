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
        findings.beginScan(playerId);
        List<RiskSignal> signals = new ArrayList<>();
        int slot = 0;
        for (ScanFinding finding : found) {
            ScannerFindingTracker.ObserveResult observed = findings.observe(playerId, finding, slot++, now);
            if (!observed.newRiskEvidence()) {
                continue;
            }
            if (finding.classification() == ScanClassification.INFO
                    || finding.classification() == ScanClassification.CUSTOM) {
                continue;
            }
            if (riskSettings.score(finding.signalType()) <= 0
                    && finding.classification() != ScanClassification.INVALID) {
                continue;
            }
            signals.add(toSignal(playerId, finding, correlationId));
        }
        findings.finishScan(playerId, now, incidents);
        return signals;
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
