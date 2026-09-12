package com.npucraft.itemguard.api;

import java.util.UUID;

/**
 * Compact incident view for diagnostics. Safe to serialize.
 */
public record IncidentView(
        UUID incidentId,
        String type,
        int score,
        String level,
        String material,
        String summary
) {
}
