package dev.itemguard.flow.model;

import dev.itemguard.item.ItemSignature;

import java.time.Instant;
import java.util.UUID;

public record ItemFlowEvent(
        UUID eventId,
        UUID playerId,
        String playerName,
        Instant timestamp,
        String material,
        ItemSignature signature,
        int amountDelta,
        FlowSource source,
        FlowDestination destination,
        SourceConfidence confidence,
        String worldName,
        UUID worldId,
        Integer x,
        Integer y,
        Integer z,
        ContainerIdentity container,
        String note,
        UUID correlationId
) {
    public boolean isGain() {
        return amountDelta > 0;
    }

    public boolean isUnexplained() {
        return source == FlowSource.UNKNOWN || confidence == SourceConfidence.UNKNOWN;
    }

    public LocationRef location() {
        if (worldName == null || x == null || y == null || z == null) {
            return null;
        }
        return new LocationRef(worldId, worldName, x, y, z);
    }
}
