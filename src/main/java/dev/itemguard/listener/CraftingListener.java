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
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Result-slot events only provide a source hint. Shift-click craft amounts are confirmed by reconciliation.
 */
public final class CraftingListener implements Listener {

    private final ExpectedFlowService expectedFlows;
    private final ReconciliationScheduler scheduler;
    private final Logger logger;

    public CraftingListener(ExpectedFlowService expectedFlows, ReconciliationScheduler scheduler, Logger logger) {
        this.expectedFlows = expectedFlows;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            ItemStack result = event.getRecipe() == null ? event.getCurrentItem() : event.getRecipe().getResult();
            if (Items.isEmpty(result)) {
                scheduler.markDirty(player);
                return;
            }
            expectedFlows.creditHint(
                    player,
                    result.getType().name(),
                    FlowSource.CRAFTING,
                    FlowDestination.PLAYER_INVENTORY,
                    SourceConfidence.INFERRED,
                    Locations.from(player),
                    null,
                    "vanilla",
                    "craft"
            );
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Craft attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            ItemStack result = event.getInventory().getResult();
            if (!Items.isEmpty(result)) {
                expectedFlows.creditHint(
                        player,
                        result.getType().name(),
                        FlowSource.CRAFTING,
                        FlowDestination.PLAYER_INVENTORY,
                        SourceConfidence.INFERRED,
                        Locations.from(player),
                        null,
                        "vanilla",
                        "smithing"
                );
            }
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Smithing attribution failed for " + event.getWhoClicked().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        try {
            Player player = event.getPlayer();
            if (event.getItemAmount() <= 0) {
                return;
            }
            expectedFlows.creditGain(
                    player,
                    event.getItemType().name(),
                    event.getItemAmount(),
                    FlowSource.SMELTING,
                    FlowDestination.PLAYER_INVENTORY,
                    SourceConfidence.VERIFIED,
                    Locations.from(event.getBlock()),
                    null,
                    "vanilla",
                    "furnace-extract",
                    null,
                    false
            );
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Furnace extract attribution failed", exception);
        }
    }
}
