package dev.itemguard.listener;

import dev.itemguard.flow.ExpectedFlowService;
import dev.itemguard.flow.ReconciliationScheduler;
import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.SourceConfidence;
import dev.itemguard.item.Items;
import dev.itemguard.util.Locations;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ground pickup and player drop have reliable amounts, so they become verified expected credits.
 */
public final class PickupDropListener implements Listener {

    private final ExpectedFlowService expectedFlows;
    private final ReconciliationScheduler scheduler;
    private final Logger logger;

    public PickupDropListener(ExpectedFlowService expectedFlows, ReconciliationScheduler scheduler, Logger logger) {
        this.expectedFlows = expectedFlows;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        try {
            ItemStack stack = event.getItem().getItemStack();
            if (Items.isEmpty(stack)) {
                return;
            }
            int picked = Math.max(0, stack.getAmount() - event.getRemaining());
            if (picked <= 0) {
                picked = stack.getAmount();
            }
            expectedFlows.creditGain(
                    player,
                    stack.getType().name(),
                    picked,
                    FlowSource.GROUND_PICKUP,
                    FlowDestination.PLAYER_INVENTORY,
                    SourceConfidence.VERIFIED,
                    Locations.from(player),
                    null,
                    "vanilla",
                    "ground-pickup",
                    null,
                    false
            );
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Pickup attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        try {
            scheduler.markDirty(event.getPlayer());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Drop tracking failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }
}
