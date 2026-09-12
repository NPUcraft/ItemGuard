package com.npucraft.itemguard.util;

import com.npucraft.itemguard.flow.model.LocationRef;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Optional;

public final class Locations {

    private Locations() {
    }

    public static LocationRef from(Location location) {
        if (location == null) {
            return LocationRef.unknown();
        }
        World world = location.getWorld();
        return new LocationRef(
                world == null ? null : world.getUID(),
                world == null ? "unknown" : world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public static LocationRef from(Block block) {
        return block == null ? LocationRef.unknown() : from(block.getLocation());
    }

    public static LocationRef from(Entity entity) {
        return entity == null ? LocationRef.unknown() : from(entity.getLocation());
    }

    public static Optional<World> worldOf(LocationRef location) {
        if (location == null) {
            return Optional.empty();
        }
        if (location.worldId() != null) {
            World byId = Bukkit.getWorld(location.worldId());
            if (byId != null) {
                return Optional.of(byId);
            }
        }
        if (location.worldName() != null && !location.worldName().isBlank() && !"unknown".equals(location.worldName())) {
            return Optional.ofNullable(Bukkit.getWorld(location.worldName()));
        }
        return Optional.empty();
    }
}
