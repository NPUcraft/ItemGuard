package dev.itemguard.risk.detector;

import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.session.PlayerGuardSession;

import java.util.List;

public interface RiskDetector {

    List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows);
}
