package com.npucraft.itemguard.api;

import java.util.UUID;

public record CreditView(
        UUID creditId,
        String material,
        String source,
        String confidence,
        int remainingAmount,
        boolean oneShot,
        String provider,
        long createdAtEpochMilli,
        long expiresAtEpochMilli,
        UUID correlationId
) {
}
