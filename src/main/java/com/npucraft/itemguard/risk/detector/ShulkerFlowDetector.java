package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.RiskSignals;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.session.PlayerGuardSession;
import com.npucraft.itemguard.session.ShulkerActivityTracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShulkerFlowDetector implements RiskDetector {

    private volatile RiskSettings settings;
    private volatile ItemValueRegistry values;

    public ShulkerFlowDetector(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    public void updateSettings(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    @Override
    public List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows) {
        Instant now = Instant.now();
        ShulkerActivityTracker tracker = session.shulker();
        List<RiskSignal> signals = new ArrayList<>();
        boolean shulkerInvolved = false;
        int transferred = 0;
        int maxItemValue = 0;
        boolean unexplainedHighValue = false;
        boolean verifiedOnly = true;
        for (ItemFlowEvent flow : newFlows) {
            if (flow.source() == FlowSource.SHULKER_BOX || flow.destination() == FlowDestination.SHULKER_BOX) {
                shulkerInvolved = true;
                transferred += Math.abs(flow.amountDelta());
                int itemValue = values.valueOf(flow.material());
                maxItemValue = Math.max(maxItemValue, itemValue);
                if (flow.confidence() != SourceConfidence.VERIFIED && flow.confidence() != SourceConfidence.TRUSTED) {
                    verifiedOnly = false;
                }
                if (flow.isUnexplained() && itemValue >= settings.detectorGates().shulkerGateMinimumItemValue()) {
                    unexplainedHighValue = true;
                }
            }
            if (flow.isUnexplained() && values.valueOf(flow.material()) >= settings.detectorGates().shulkerGateMinimumItemValue()) {
                unexplainedHighValue = true;
            }
        }
        if (!shulkerInvolved) {
            return signals;
        }
        tracker.recordTransfer(now, settings.shulkerFlowWindow());
        boolean rapid = tracker.isRapid(settings.shulkerRapidTransferMillis());
        boolean highFlow = tracker.transfers() >= settings.shulkerHighFlowTransfers();
        int gateValue = settings.detectorGates().shulkerGateMinimumItemValue();
        boolean gated = unexplainedHighValue
                || (rapid && maxItemValue >= gateValue && !verifiedOnly)
                || (highFlow && unexplainedHighValue)
                || (rapid && unexplainedHighValue);
        if (highFlow) {
            int score = gated ? settings.score(SignalType.SHULKER_HIGH_FLOW) : 0;
            if (score > 0) {
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
                                "windowMillis", Long.toString(settings.shulkerFlowWindow().toMillis()),
                                "gated", "true"
                        ),
                        score
                ));
            }
        }
        if (rapid) {
            int configured = settings.score(SignalType.SHULKER_RAPID_TRANSFER);
            int score = !gated ? 0 : (configured > 0 ? configured : 8);
            if (score > 0) {
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
                        "Rapid shulker transfer with additional risk gate",
                        Map.of(
                                "rapidMillis", Long.toString(settings.shulkerRapidTransferMillis()),
                                "itemValue", Integer.toString(maxItemValue)
                        ),
                        score
                ));
            }
        }
        return signals;
    }
}
