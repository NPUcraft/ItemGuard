package dev.itemguard.harness;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class FixtureService implements Listener {

    static final int PLATFORM_Y = 100;
    static final int SPAWN_X = 16;
    static final int SPAWN_Z = 16;
    static final int CHEST_X = 14;
    static final int CRAFT_X = 18;
    static final int ANVIL_X = 20;
    static final int GRIND_X = 22;
    static final int SMITH_X = 24;
    static final int SHULKER_Z = 18;
    static final int BUNDLE_CHEST_X = 12;

    private final JavaPlugin plugin;
    private volatile boolean ready;

    private volatile boolean built;

    private volatile Location lastDeath;

    FixtureService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureReady();
            // Do not teleport/reset when HuskSync is applying a restored inventory.
            if (Bukkit.getPluginManager().getPlugin("HuskSync") == null) {
                resetPlayer(player, false);
            } else {
                player.setFallDistance(0f);
                player.setFireTicks(0);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        rememberDeath(event.getEntity().getLocation());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location at = lastDeath == null ? spawn(overworld()) : lastDeath;
            for (Entity entity : overworld().getNearbyEntities(at, 8, 8, 8)) {
                if (entity instanceof Item item) {
                    // Keep a short delay so tests can observe empty inventory before pickup.
                    item.setPickupDelay(Math.max(item.getPickupDelay(), 20));
                }
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location target = lastDeath == null ? spawn(overworld()) : lastDeath.clone();
        event.setRespawnLocation(target);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getLocation().distanceSquared(target) > 4) {
                player.teleport(target);
            }
            player.setFallDistance(0f);
            player.setFireTicks(0);
        });
    }

    void rememberDeath(Location location) {
        if (location != null) {
            lastDeath = location.clone();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Leave ItemGuard session lifecycle to the product plugin.
    }

    void ensureReady() {
        World world = overworld();
        world.setSpawnLocation(spawn(world));
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        if (!built) {
            buildPlatform(world);
            plugin.getLogger().info("Test fixture ready at " + spawn(world));
            built = true;
        }
        ready = true;
    }

    boolean ready() {
        return ready;
    }

    World overworld() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            throw new IllegalStateException("No world loaded");
        }
        return world;
    }

    Location spawn(World world) {
        return new Location(world, SPAWN_X + 0.5, PLATFORM_Y + 1, SPAWN_Z + 0.5, 0f, 0f);
    }

    Location chestLocation() {
        World world = overworld();
        return new Location(world, CHEST_X, PLATFORM_Y + 1, SPAWN_Z);
    }

    Location craftingLocation() {
        World world = overworld();
        return new Location(world, CRAFT_X, PLATFORM_Y + 1, SPAWN_Z);
    }

    Location placedShulkerLocation() {
        World world = overworld();
        return new Location(world, SPAWN_X, PLATFORM_Y + 1, SHULKER_Z);
    }

    Location bundleChestLocation() {
        World world = overworld();
        return new Location(world, BUNDLE_CHEST_X, PLATFORM_Y + 1, SPAWN_Z);
    }

    void resetPlayer(Player player, boolean clearInventory) {
        ensureReady();
        Location spawn = spawn(player.getWorld() == null ? overworld() : player.getWorld());
        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setExp(0f);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.closeInventory();
        if (clearInventory) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            player.setItemOnCursor(null);
        }
        player.setOp(false);
    }

    void clearNearby(Player player) {
        World world = player.getWorld();
        Location center = spawn(world);
        for (Entity entity : world.getNearbyEntities(center, 48, 16, 48)) {
            if (entity instanceof Item) {
                entity.remove();
            }
        }
        clearChest(chestBlock());
        clearChest(bundleChestBlock());
        resetPlacedShulker();
    }

    Block chestBlock() {
        World world = overworld();
        Block block = world.getBlockAt(CHEST_X, PLATFORM_Y + 1, SPAWN_Z);
        if (block.getType() != Material.CHEST) {
            block.setType(Material.CHEST, false);
        }
        return block;
    }

    Block bundleChestBlock() {
        World world = overworld();
        Block block = world.getBlockAt(BUNDLE_CHEST_X, PLATFORM_Y + 1, SPAWN_Z);
        if (block.getType() != Material.CHEST) {
            block.setType(Material.CHEST, false);
        }
        return block;
    }

    Block craftingBlock() {
        World world = overworld();
        return world.getBlockAt(CRAFT_X, PLATFORM_Y + 1, SPAWN_Z);
    }

    Block placedShulkerBlock() {
        World world = overworld();
        Block block = world.getBlockAt(SPAWN_X, PLATFORM_Y + 1, SHULKER_Z);
        if (block.getType() != Material.SHULKER_BOX) {
            block.setType(Material.SHULKER_BOX, false);
        }
        return block;
    }

    void clearChest(Block block) {
        if (block.getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
            chest.update();
        }
    }

    void resetPlacedShulker() {
        Block block = placedShulkerBlock();
        if (block.getState() instanceof ShulkerBox box) {
            box.getInventory().clear();
            box.update(true, false);
        }
    }

    Inventory chestInventory() {
        Block block = chestBlock();
        if (block.getState() instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        throw new IllegalStateException("Chest fixture missing");
    }

    Inventory bundleChestInventory() {
        Block block = bundleChestBlock();
        if (block.getState() instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        throw new IllegalStateException("Bundle chest fixture missing");
    }

    Inventory placedShulkerInventory() {
        Block block = placedShulkerBlock();
        if (block.getState() instanceof ShulkerBox box) {
            return box.getInventory();
        }
        throw new IllegalStateException("Placed shulker fixture missing");
    }

    private void buildPlatform(World world) {
        for (int x = 0; x <= 32; x++) {
            for (int z = 0; z <= 32; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.STONE, false);
                for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + 4; y++) {
                    Block air = world.getBlockAt(x, y, z);
                    if (!air.getType().isAir()
                            && air.getType() != Material.CHEST
                            && air.getType() != Material.CRAFTING_TABLE
                            && air.getType() != Material.ANVIL
                            && air.getType() != Material.GRINDSTONE
                            && air.getType() != Material.SMITHING_TABLE
                            && air.getType() != Material.SHULKER_BOX) {
                        air.setType(Material.AIR, false);
                    }
                }
            }
        }
        world.getBlockAt(CHEST_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.CHEST, false);
        world.getBlockAt(BUNDLE_CHEST_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.CHEST, false);
        world.getBlockAt(CRAFT_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.CRAFTING_TABLE, false);
        world.getBlockAt(ANVIL_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.ANVIL, false);
        world.getBlockAt(GRIND_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.GRINDSTONE, false);
        world.getBlockAt(SMITH_X, PLATFORM_Y + 1, SPAWN_Z).setType(Material.SMITHING_TABLE, false);
        world.getBlockAt(SPAWN_X, PLATFORM_Y + 1, SHULKER_Z).setType(Material.SHULKER_BOX, false);
        for (int x = 0; x <= 32; x++) {
            for (int z = 0; z <= 32; z++) {
                world.getBlockAt(x, PLATFORM_Y + 5, z).setType(Material.BARRIER, false);
            }
        }
        world.getChunkAt(SPAWN_X >> 4, SPAWN_Z >> 4).load(true);
    }
}
