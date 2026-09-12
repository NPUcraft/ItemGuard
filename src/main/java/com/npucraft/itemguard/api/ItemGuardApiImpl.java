package com.npucraft.itemguard.api;

import com.npucraft.itemguard.flow.ExpectedFlowService;
import com.npucraft.itemguard.flow.ReconciliationScheduler;
import com.npucraft.itemguard.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class ItemGuardApiImpl implements ItemGuardApi, ExternalItemSourceProvider {

    private final ExpectedFlowService expectedFlows;
    private final ReconciliationScheduler scheduler;
    private final ItemGuardDiagnostics diagnostics;

    public ItemGuardApiImpl(
            ExpectedFlowService expectedFlows,
            ReconciliationScheduler scheduler,
            ItemGuardDiagnostics diagnostics
    ) {
        this.expectedFlows = expectedFlows;
        this.scheduler = scheduler;
        this.diagnostics = diagnostics;
    }

    @Override
    public ItemGuardDiagnostics diagnostics() {
        return diagnostics;
    }

    @Override
    public String id() {
        return "itemguard-api";
    }

    @Override
    public void beginTransaction(UUID playerId, String provider, String transactionId) {
        beginTransaction(playerId, transactionId);
    }

    @Override
    public void beginTransaction(UUID playerId, String transactionId) {
        // 1.0 credits are TTL-based; transaction IDs are stored on the credit correlation.
    }

    @Override
    public void recordExpectedGain(UUID playerId, String material, int amount, String provider, String transactionId) {
        if (playerId == null || material == null || amount <= 0) {
            return;
        }
        expectedFlows.creditPluginGain(playerId, material, amount, provider == null || provider.isBlank() ? id() : provider, transactionId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            scheduler.markDirty(player);
        }
    }

    @Override
    public void recordExpectedGain(UUID playerId, ItemStack item, int amount, String provider, String transactionId) {
        if (Items.isEmpty(item)) {
            return;
        }
        recordExpectedGain(playerId, item.getType().name(), amount <= 0 ? item.getAmount() : amount, provider, transactionId);
    }

    @Override
    public void recordExpectedGain(UUID playerId, String material, int amount, String transactionId) {
        recordExpectedGain(playerId, material, amount, id(), transactionId);
    }

    @Override
    public void recordExpectedLoss(UUID playerId, String material, int amount, String provider, String transactionId) {
        recordExpectedLoss(playerId, material, amount, transactionId);
    }

    @Override
    public void recordExpectedLoss(UUID playerId, String material, int amount, String transactionId) {
        // 1.0 risk is gain-centric; losses are ignored for unknown-gain correlation.
    }

    @Override
    public void endTransaction(UUID playerId, String transactionId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            scheduler.markDirty(player);
        }
    }
}
