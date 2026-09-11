package dev.itemguard.flow;

import dev.itemguard.config.PluginSettings;
import dev.itemguard.flow.model.ContainerIdentity;
import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.LocationRef;
import dev.itemguard.flow.model.SourceConfidence;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ExpectedFlowService {

    private final ExpectedFlowLedger ledger;
    private volatile PluginSettings settings;

    public ExpectedFlowService(ExpectedFlowLedger ledger, PluginSettings settings) {
        this.ledger = ledger;
        this.settings = settings;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public ExpectedFlowLedger ledger() {
        return ledger;
    }

    public UUID creditGain(
            Player player,
            String material,
            int amount,
            FlowSource source,
            FlowDestination destination,
            SourceConfidence confidence,
            LocationRef location,
            ContainerIdentity container,
            String provider,
            String note,
            UUID correlationId,
            boolean oneShot
    ) {
        if (player == null || material == null || amount <= 0) {
            return correlationId;
        }
        UUID correlation = correlationId == null ? UUID.randomUUID() : correlationId;
        Instant now = Instant.now();
        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(),
                player.getUniqueId(),
                material,
                null,
                amount,
                source,
                destination,
                confidence,
                now,
                now.plus(Duration.ofMillis(settings.expectedFlowTtlMillis())),
                correlation,
                note,
                location,
                container,
                provider,
                oneShot
        ));
        return correlation;
    }

    public UUID creditHint(
            Player player,
            String material,
            FlowSource source,
            FlowDestination destination,
            SourceConfidence confidence,
            LocationRef location,
            ContainerIdentity container,
            String provider,
            String note
    ) {
        return creditGain(player, material, 10_000, source, destination, confidence, location, container, provider, note, null, true);
    }

    public UUID creditPluginGain(UUID playerId, String material, int amount, String provider, String transactionId) {
        Instant now = Instant.now();
        UUID correlation = transactionId == null ? UUID.randomUUID() : UUID.nameUUIDFromBytes(transactionId.getBytes());
        ledger.add(new ExpectedFlowCredit(
                UUID.randomUUID(),
                playerId,
                material,
                null,
                amount,
                FlowSource.PLUGIN,
                FlowDestination.PLAYER_INVENTORY,
                SourceConfidence.TRUSTED,
                now,
                now.plus(Duration.ofMillis(settings.expectedFlowTtlMillis())),
                correlation,
                provider,
                null,
                null,
                provider == null ? "api" : provider,
                false
        ));
        return correlation;
    }

    public static ContainerIdentity containerIdentity(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        Location location = null;
        String type = inventory.getType().name();
        if (holder instanceof DoubleChest chest) {
            location = chest.getLocation();
            type = "DOUBLE_CHEST";
        } else if (holder instanceof BlockState state) {
            location = state.getLocation();
        } else if (holder instanceof org.bukkit.entity.Entity entity) {
            location = entity.getLocation();
        }
        if (location == null || location.getWorld() == null) {
            return new ContainerIdentity(type, null, "unknown", 0, 0, 0);
        }
        return new ContainerIdentity(
                type,
                location.getWorld().getUID(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public static boolean isShulker(Inventory inventory) {
        return inventory != null && "SHULKER_BOX".equals(inventory.getType().name());
    }

    public static FlowSource sourceFor(Inventory inventory) {
        if (inventory == null) {
            return FlowSource.UNKNOWN;
        }
        return switch (inventory.getType()) {
            case SHULKER_BOX -> FlowSource.SHULKER_BOX;
            case WORKBENCH, CRAFTING, CRAFTER -> FlowSource.CRAFTING;
            case FURNACE, BLAST_FURNACE, SMOKER -> FlowSource.SMELTING;
            case BREWING -> FlowSource.BREWING;
            case MERCHANT -> FlowSource.VILLAGER_TRADE;
            case PLAYER -> FlowSource.PLAYER_INVENTORY;
            default -> FlowSource.CONTAINER;
        };
    }

    public static FlowDestination destinationFor(Inventory inventory) {
        if (inventory == null) {
            return FlowDestination.UNKNOWN;
        }
        return switch (inventory.getType()) {
            case SHULKER_BOX -> FlowDestination.SHULKER_BOX;
            case WORKBENCH, CRAFTING, CRAFTER -> FlowDestination.CRAFTING_INPUT;
            case PLAYER -> FlowDestination.PLAYER_INVENTORY;
            default -> FlowDestination.CONTAINER;
        };
    }
}
