package com.npucraft.itemguard.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Public integration surface. Other plugins can explain legitimate item grants.
 * Internal services are not exposed here.
 */
public interface ItemGuardApi {

    /**
     * Read-only diagnostics. Does not expose mutable sessions or live inventories.
     */
    @Experimental
    ItemGuardDiagnostics diagnostics();

    void beginTransaction(UUID playerId, String provider, String transactionId);

    void recordExpectedGain(UUID playerId, String material, int amount, String provider, String transactionId);

    void recordExpectedGain(UUID playerId, ItemStack item, int amount, String provider, String transactionId);

    void recordExpectedLoss(UUID playerId, String material, int amount, String provider, String transactionId);

    void endTransaction(UUID playerId, String transactionId);
}
