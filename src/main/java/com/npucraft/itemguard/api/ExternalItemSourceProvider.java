package com.npucraft.itemguard.api;

import java.util.UUID;

/**
 * Future shop/crate/custom-item plugins can implement this to explain legal inventory mutations.
 * ItemGuard 1.0 only ships the vanilla + HuskSync providers.
 */
public interface ExternalItemSourceProvider {

    String id();

    void beginTransaction(UUID playerId, String transactionId);

    void recordExpectedGain(UUID playerId, String material, int amount, String transactionId);

    void recordExpectedLoss(UUID playerId, String material, int amount, String transactionId);

    void endTransaction(UUID playerId, String transactionId);
}
