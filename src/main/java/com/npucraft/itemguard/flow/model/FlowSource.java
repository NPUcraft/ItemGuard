package com.npucraft.itemguard.flow.model;

/**
 * Where an item quantity came from. UNKNOWN is a first-class source, not a boolean flag.
 */
public enum FlowSource {
    GROUND_PICKUP,
    PLAYER_INVENTORY,
    CONTAINER,
    SHULKER_BOX,
    CRAFTING,
    SMELTING,
    BREWING,
    VILLAGER_TRADE,
    PLAYER_TRADE,
    COMMAND,
    PLUGIN,
    HUSKSYNC_DATA_APPLY,
    CREATIVE_INVENTORY,
    UNKNOWN
}
