package dev.itemguard.flow.model;

import java.util.UUID;

public record ContainerIdentity(String type, UUID worldId, String worldName, int x, int y, int z) {

    public LocationRef location() {
        return new LocationRef(worldId, worldName, x, y, z);
    }

    public String format() {
        return type + " @ " + worldName + " " + x + "," + y + "," + z;
    }
}
