package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;

import java.util.Map;
import java.util.Objects;

public record ScanFinding(
        String ruleId,
        SignalType signalType,
        ScanClassification classification,
        Severity severity,
        String material,
        ItemSignature signature,
        int amount,
        String description,
        Map<String, String> evidence
) {
    public ScanFinding {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(signalType, "signalType");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(description, "description");
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
