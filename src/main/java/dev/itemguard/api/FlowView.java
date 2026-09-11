package dev.itemguard.api;

import java.util.UUID;

public record FlowView(
        UUID eventId,
        long timestampEpochMilli,
        String material,
        int amountDelta,
        String source,
        String destination,
        String confidence,
        String worldName,
        Integer x,
        Integer y,
        Integer z,
        String container,
        String note
) {
}
