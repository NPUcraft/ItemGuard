package com.npucraft.itemguard.risk.incident;

import java.util.List;
import java.util.UUID;

/**
 * Staff-chat alert is allowed only when an incident crosses HIGH or CRITICAL.
 */
public record IncidentEscalation(
        UUID incidentId,
        IncidentType type,
        IncidentAlertLevel from,
        IncidentAlertLevel to,
        int score,
        String material,
        int amount,
        String sourceSummary,
        List<String> evidence,
        UUID correlationId
) {
    public IncidentEscalation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        material = material == null ? "*" : material;
    }

    public String transitionName() {
        return from.name() + "_TO_" + to.name();
    }

    public boolean isStaffAlert() {
        return to == IncidentAlertLevel.HIGH || to == IncidentAlertLevel.CRITICAL;
    }
}
