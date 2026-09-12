package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.RiskSignals;
import com.npucraft.itemguard.risk.UnknownGainScorer;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.session.PlayerGuardSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UnexplainedGainDetector implements RiskDetector {

    private volatile RiskSettings settings;
    private volatile ItemValueRegistry values;

    public UnexplainedGainDetector(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    public void updateSettings(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    @Override
    public List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows) {
        List<RiskSignal> signals = new ArrayList<>();
        for (ItemFlowEvent flow : newFlows) {
            if (!flow.isGain() || !flow.isUnexplained() || flow.amountDelta() < settings.unexplainedMinAmount()) {
                continue;
            }
            int score = UnknownGainScorer.score(flow.material(), flow.amountDelta(), values, settings.unknownGain());
            Severity severity = score >= 25 ? Severity.HIGH : (score >= 10 ? Severity.SUSPICIOUS : Severity.INFO);
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.UNEXPLAINED_ITEM_GAIN,
                    settings,
                    severity,
                    flow.material(),
                    flow.signature(),
                    flow.amountDelta(),
                    flow.correlationId(),
                    flow.eventId(),
                    flow.amountDelta() + " " + flow.material() + " unexplained (" + flow.confidence().name() + ")",
                    Map.of(
                            "source", "UNKNOWN",
                            "confidence", flow.confidence().name(),
                            "itemValue", Integer.toString(values.valueOf(flow.material())),
                            "weightedValue", Integer.toString(values.weighted(flow.material(), flow.amountDelta()))
                    ),
                    score
            ));
        }
        return signals;
    }
}
