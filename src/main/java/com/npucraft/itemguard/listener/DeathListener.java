package com.npucraft.itemguard.listener;

import com.npucraft.itemguard.flow.ReconciliationScheduler;
import com.npucraft.itemguard.flow.ReconciliationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Death empties or restores inventory without being an unexplained mutation.
 * Drops later picked up are attributed as GROUND_PICKUP by PickupDropListener.
 */
public final class DeathListener implements Listener {

    private final ReconciliationService reconciliation;
    private final ReconciliationScheduler scheduler;
    private final Logger logger;

    public DeathListener(ReconciliationService reconciliation, ReconciliationScheduler scheduler, Logger logger) {
        this.reconciliation = reconciliation;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        try {
            boolean keep = event.getKeepInventory();
            if (keep) {
                scheduler.markDirty(player);
            } else {
                reconciliation.establishBaseline(player);
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Death baseline update failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        try {
            scheduler.markDirty(event.getPlayer());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Respawn tracking failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }
}
