package com.npucraft.itemguard.flow.model;

import java.util.UUID;

/**
 * Detached world coordinates. Never stores a Bukkit World reference.
 */
public record LocationRef(UUID worldId, String worldName, int x, int y, int z) {

    public static LocationRef unknown() {
        return new LocationRef(null, "unknown", 0, 0, 0);
    }

    public String format() {
        return worldName + " " + x + "," + y + "," + z;
    }
}
