package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.ContainerIdentity;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.LocationRef;
import com.npucraft.itemguard.flow.model.SourceConfidence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable expected inventory credit. {@code remainingAmount} shrinks by replacement, never by mutation.
 */
public record ExpectedFlowCredit(
        UUID creditId,
        UUID playerId,
        String material,
        String signatureHash,
        int remainingAmount,
        FlowSource source,
        FlowDestination destination,
        SourceConfidence confidence,
        Instant createdAt,
        Instant expiresAt,
        UUID correlationId,
        String note,
        LocationRef location,
        ContainerIdentity container,
        String provider,
        boolean oneShot
) {
    public ExpectedFlowCredit {
        Objects.requireNonNull(creditId, "creditId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(provider, "provider");
        remainingAmount = Math.max(0, remainingAmount);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean matches(String actualMaterial) {
        return material.equals(actualMaterial) || "*".equals(material);
    }

    public ExpectedFlowCredit consume(int amount) {
        int next = Math.max(0, remainingAmount - Math.max(0, amount));
        return new ExpectedFlowCredit(
                creditId,
                playerId,
                material,
                signatureHash,
                next,
                source,
                destination,
                confidence,
                createdAt,
                expiresAt,
                correlationId,
                note,
                location,
                container,
                provider,
                oneShot
        );
    }
}
