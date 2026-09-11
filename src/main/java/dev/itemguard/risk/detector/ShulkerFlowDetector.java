package dev.itemguard.risk.detector;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.RiskSignals;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.session.PlayerGuardSession;
import dev.itemguard.session.ShulkerActivityTracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShulkerFlowDetector implements RiskDetector {

    private volatile RiskSettings settings;

    public ShulkerFlowDetector(RiskSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(RiskSettings settings) {
        this.settings = settings;
    }

    @Override
    public List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows) {
        Instant now = Instant.now();
        ShulkerActivityTracker tracker = session.shulker();
        List<RiskSignal> signals = new ArrayList<>();
        boolean shulkerInvolved = false;
        int transferred = 0;
        for (ItemFlowEvent flow : newFlows) {
            if (flow.source() == FlowSource.SHULKER_BOX || flow.destination() == dev.itemguard.flow.model.FlowDestination.SHULKER_BOX) {
                shulkerInvolved = true;
                transferred += Math.abs(flow.amountDelta());
            }
        }
        if (!shulkerInvolved) {
            return signals;
        }
        tracker.recordTransfer(now, settings.shulkerFlowWindow());
        if (tracker.transfers() >= settings.shulkerHighFlowTransfers()) {
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.SHULKER_HIGH_FLOW,
                    settings,
                    Severity.SUSPICIOUS,
                    "*",
                    null,
                    transferred,
                    newFlows.isEmpty() ? null : newFlows.get(0).correlationId(),
                    null,
                    tracker.transfers() + " shulker transfers within " + settings.shulkerFlowWindow().toSeconds() + "s",
                    Map.of(
                            "transfers", Integer.toString(tracker.transfers()),
                            "windowMillis", Long.toString(settings.shulkerFlowWindow().toMillis())
                    )
            ));
        }
        if (tracker.isRapid(settings.shulkerRapidTransferMillis())) {
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.SHULKER_RAPID_TRANSFER,
                    settings,
                    Severity.SUSPICIOUS,
                    "*",
                    null,
                    transferred,
                    newFlows.isEmpty() ? null : newFlows.get(0).correlationId(),
                    null,
                    "Rapid shulker transfer pattern",
                    Map.of("rapidMillis", Long.toString(settings.shulkerRapidTransferMillis()))
            ));
        }
        return signals;
    }
}
