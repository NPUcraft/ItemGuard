package dev.itemguard.listener;

import dev.itemguard.flow.ExpectedFlowService;
import dev.itemguard.flow.ReconciliationScheduler;
import dev.itemguard.flow.model.ContainerIdentity;
import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.LocationRef;
import dev.itemguard.flow.model.SourceConfidence;
import dev.itemguard.item.Items;
import dev.itemguard.session.PlayerGuardSession;
import dev.itemguard.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Container/player clicks are not final until the next tick. This listener only records source hints.
 * Amounts come from post-event inventory reconciliation, never from clicked stack size or raw slot count.
 */
public final class InventoryInteractionListener implements Listener {

    private final ExpectedFlowService expectedFlows;
    private final ReconciliationScheduler scheduler;
    private final SessionManager sessions;
    private final Logger logger;

    public InventoryInteractionListener(
            ExpectedFlowService expectedFlows,
            ReconciliationScheduler scheduler,
            SessionManager sessions,
            Logger logger
    ) {
        this.expectedFlows = expectedFlows;
        this.scheduler = scheduler;
        this.sessions = sessions;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        try {
            Inventory top = event.getInventory();
            if (ExpectedFlowService.isShulker(top)) {
                sessions.require(player, Instant.now()).shulker().open(Instant.now());
            }
            ContainerIdentity identity = ExpectedFlowService.containerIdentity(top);
            if (!isPlayerInventory(top)) {
                sessions.require(player, Instant.now()).setLastContainer(identity);
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Inventory open tracking failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        try {
            if (ExpectedFlowService.isShulker(event.getInventory())) {
                sessions.require(player, Instant.now()).shulker().close(Instant.now());
            }
            PlayerGuardSession session = sessions.require(player, Instant.now());
            session.setDiscardHintsAfterReconcile(true);
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Inventory close tracking failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            Inventory clicked = event.getClickedInventory();
            Inventory top = event.getView().getTopInventory();
            Inventory bottom = event.getView().getBottomInventory();
            if (clicked == null) {
                scheduler.markDirty(player);
                return;
            }
            boolean clickedTop = clicked.equals(top);
            boolean topIsPlayer = isPlayerInventory(top);
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            InventoryAction action = event.getAction();
            if (!topIsPlayer && (clickedTop || action == InventoryAction.MOVE_TO_OTHER_INVENTORY && clickedTop)) {
                hintFromContainer(player, top, current, action);
            } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && !topIsPlayer && clicked.equals(bottom)) {
                if (!Items.isEmpty(current)) {
                    sessions.require(player, Instant.now()).setLastContainer(ExpectedFlowService.containerIdentity(top));
                }
            } else if (!topIsPlayer && isResultSlot(event) && !Items.isEmpty(current) && !isCraftHandledElsewhere(top.getType())) {
                expectedFlows.creditHint(
                        player,
                        current.getType().name(),
                        sourceForResult(top.getType()),
                        FlowDestination.PLAYER_INVENTORY,
                        SourceConfidence.INFERRED,
                        locationOf(top),
                        ExpectedFlowService.containerIdentity(top),
                        "vanilla",
                        "result-slot"
                );
            }
            if (!Items.isEmpty(cursor) && clickedTop && !topIsPlayer && isPlace(action)) {
                sessions.require(player, Instant.now()).setLastContainer(ExpectedFlowService.containerIdentity(top));
            }
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Inventory click attribution failed for " + player.getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            Inventory top = event.getView().getTopInventory();
            boolean movedFromTop = false;
            for (int raw : event.getRawSlots()) {
                if (raw < event.getView().getTopInventory().getSize() && !isPlayerInventory(top)) {
                    movedFromTop = true;
                    break;
                }
            }
            ItemStack oldCursor = event.getOldCursor();
            if (movedFromTop && !Items.isEmpty(oldCursor) && !isPlayerInventory(top)) {
                expectedFlows.creditHint(
                        player,
                        oldCursor.getType().name(),
                        ExpectedFlowService.sourceFor(top),
                        FlowDestination.PLAYER_INVENTORY,
                        SourceConfidence.INFERRED,
                        locationOf(top),
                        ExpectedFlowService.containerIdentity(top),
                        "vanilla",
                        "inventory-drag"
                );
                sessions.require(player, Instant.now()).setLastContainer(ExpectedFlowService.containerIdentity(top));
            }
            scheduler.markDirty(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Inventory drag attribution failed for " + player.getUniqueId(), exception);
        }
    }

    private void hintFromContainer(Player player, Inventory top, ItemStack current, InventoryAction action) {
        if (Items.isEmpty(current) || isPlayerInventory(top)) {
            return;
        }
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.SWAP_WITH_CURSOR) {
            expectedFlows.creditHint(
                    player,
                    current.getType().name(),
                    ExpectedFlowService.sourceFor(top),
                    FlowDestination.PLAYER_INVENTORY,
                    SourceConfidence.VERIFIED,
                    locationOf(top),
                    ExpectedFlowService.containerIdentity(top),
                    "vanilla",
                    "container-click"
            );
            sessions.require(player, Instant.now()).setLastContainer(ExpectedFlowService.containerIdentity(top));
        }
    }

    private static boolean isPlace(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR;
    }

    private static boolean isResultSlot(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.RESULT;
    }

    private static boolean isCraftHandledElsewhere(InventoryType type) {
        return type == InventoryType.WORKBENCH || type == InventoryType.CRAFTING || type == InventoryType.CRAFTER
                || type == InventoryType.SMITHING;
    }

    private static boolean isPlayerInventory(Inventory inventory) {
        return inventory instanceof PlayerInventory || inventory.getType() == InventoryType.PLAYER || inventory.getType() == InventoryType.CRAFTING;
    }

    private static FlowSource sourceForResult(InventoryType type) {
        return switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER -> FlowSource.SMELTING;
            case BREWING -> FlowSource.BREWING;
            case MERCHANT -> FlowSource.VILLAGER_TRADE;
            case ANVIL, GRINDSTONE -> FlowSource.CRAFTING;
            default -> FlowSource.CRAFTING;
        };
    }

    private static LocationRef locationOf(Inventory inventory) {
        ContainerIdentity identity = ExpectedFlowService.containerIdentity(inventory);
        return identity == null ? LocationRef.unknown() : identity.location();
    }
}
