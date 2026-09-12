package com.npucraft.itemguard.api;

import java.util.Map;

public record ScanFindingView(
        String ruleId,
        String signalType,
        String classification,
        String severity,
        String material,
        int amount,
        String description,
        Map<String, String> evidence
) {
}
