package com.npucraft.itemguard.listener;

import com.npucraft.itemguard.flow.ReconciliationScheduler;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catches plugin mutations such as Inventory#addItem that do not fire click/pickup events.
 * Attribution still comes from expected credits + reconciliation, not from this event's contents.
 */
public final class InventorySlotChangeListener implements Listener {

    private final ReconciliationScheduler scheduler;
    private final Logger logger;

    public InventorySlotChangeListener(ReconciliationScheduler scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        try {
            scheduler.markDirty(event.getPlayer());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Slot change tracking failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }
}
