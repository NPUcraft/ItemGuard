package dev.itemguard.risk.detector;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.RiskSignals;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.session.PlayerGuardSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UnexplainedGainDetector implements RiskDetector {

    private volatile RiskSettings settings;

    public UnexplainedGainDetector(RiskSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(RiskSettings settings) {
        this.settings = settings;
    }

    @Override
    public List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows) {
        List<RiskSignal> signals = new ArrayList<>();
        for (ItemFlowEvent flow : newFlows) {
            if (!flow.isGain() || !flow.isUnexplained() || flow.amountDelta() < settings.unexplainedMinAmount()) {
                continue;
            }
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.UNEXPLAINED_ITEM_GAIN,
                    settings,
                    Severity.HIGH,
                    flow.material(),
                    flow.signature(),
                    flow.amountDelta(),
                    flow.correlationId(),
                    flow.eventId(),
                    flow.amountDelta() + " " + flow.material() + " gained from UNKNOWN SOURCE",
                    Map.of(
                            "source", "UNKNOWN",
                            "confidence", flow.confidence().name()
                    )
            ));
        }
        return signals;
    }
}
