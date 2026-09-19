package com.npucraft.itemguard.listener;

import com.npucraft.itemguard.flow.ExpectedFlowService;
import com.npucraft.itemguard.flow.ReconciliationScheduler;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.util.Locations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vanilla bucket transforms return a different material. Exact credits explain the replacement item.
 * This is not a BUCKET whitelist: plugin-injected empty buckets still reconcile as UNKNOWN.
 */
public final class VanillaBucketListener implements Listener {

    private final ExpectedFlowService expectedFlows;
    private final ReconciliationScheduler scheduler;
    private final Logger logger;

    public VanillaBucketListener(
            ExpectedFlowService expectedFlows,
            ReconciliationScheduler scheduler,
            Logger logger
    ) {
        this.expectedFlows = expectedFlows;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        try {
            ItemStack result = event.getItemStack();
            String material = Items.isEmpty(result) ? Material.BUCKET.name() : result.getType().name();
            credit(player, material, 1, "bucket-empty");
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Bucket empty attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        try {
            ItemStack result = event.getItemStack();
            if (Items.isEmpty(result)) {
                scheduler.markDirty(player);
                return;
            }
            credit(player, result.getType().name(), Math.max(1, result.getAmount()), "bucket-fill");
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Bucket fill attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBucket(PlayerBucketEntityEvent event) {
        Player player = event.getPlayer();
        try {
            ItemStack result = event.getEntityBucket();
            if (Items.isEmpty(result)) {
                scheduler.markDirty(player);
                return;
            }
            credit(player, result.getType().name(), Math.max(1, result.getAmount()), "bucket-entity");
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Bucket entity attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        try {
            ItemStack consumed = event.getItem();
            if (Items.isEmpty(consumed) || consumed.getType() != Material.MILK_BUCKET) {
                return;
            }
            credit(player, Material.BUCKET.name(), 1, "milk-consume");
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Milk bucket attribution failed for " + player.getUniqueId(), exception);
        }
    }

    private void credit(Player player, String material, int amount, String note) {
        expectedFlows.creditGain(
                player,
                material,
                amount,
                FlowSource.PLAYER_INVENTORY,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.VERIFIED,
                Locations.from(player),
                null,
                "vanilla",
                note,
                null,
                false
        );
        scheduler.markDirty(player);
    }
}
