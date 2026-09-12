package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.session.PlayerGuardSession;

import java.util.List;

public interface RiskDetector {

    List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows);
}
