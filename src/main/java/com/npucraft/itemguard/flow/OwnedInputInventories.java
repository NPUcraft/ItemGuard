package com.npucraft.itemguard.flow;

/**
 * Player-owned crafting/trade input UIs. External chests and machine inventories stay excluded.
 */
public final class OwnedInputInventories {

    private OwnedInputInventories() {
    }

    public static boolean isOwned(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        return switch (typeName) {
            case "CRAFTING", "WORKBENCH", "MERCHANT", "ANVIL", "GRINDSTONE", "SMITHING",
                 "STONECUTTER", "LOOM", "CARTOGRAPHY", "ENCHANTING" -> true;
            default -> false;
        };
    }
}
