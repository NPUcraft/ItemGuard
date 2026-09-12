package com.npucraft.itemguard.harness;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.npucraft.itemguard.api.ItemGuardApiProvider;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScenarioService {

    private final ItemGuardTestHarnessPlugin plugin;
    private final FixtureService fixture;

    ScenarioService(ItemGuardTestHarnessPlugin plugin, FixtureService fixture) {
        this.plugin = plugin;
        this.fixture = fixture;
    }

    Map<String, Object> reset(Player player) {
        fixture.ensureReady();
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getWorld().setGameRule(GameRule.KEEP_INVENTORY, false);
        return result(player, "reset", Map.of());
    }

    Map<String, Object> prepare(Player player, String scenario) {
        fixture.ensureReady();
        String name = scenario == null ? "" : scenario.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "pickup-one" -> dropDiamonds(player, 1);
            case "pickup-stack", "pickup-64" -> dropDiamonds(player, 64);
            case "pickup-32" -> dropDiamonds(player, 32);
            case "chest-normal", "chest-shift" -> fillChest(player, 64);
            case "chest-dirt" -> fillChestMaterial(player, Material.DIRT, 1);
            case "chest-put-dirt" -> putChest(player, Material.DIRT, 1);
            case "chest-number-key" -> chestNumberKey(player);
            case "chest-double-click" -> chestDoubleClick(player);
            case "chest-drag" -> fillChest(player, 64);
            case "shulker-dirt" -> placedShulkerMaterial(player, Material.DIRT, 1);
            case "shulker-put-dirt" -> putPlacedShulker(player, Material.DIRT, 1);
            case "clear-inventory" -> clearInventory(player);
            case "craft" -> craft(player);
            case "craft-shift" -> craftShift(player);
            case "furnace-output" -> furnaceOutput(player);
            case "stonecutter" -> stonecutter(player);
            case "merchant" -> merchant(player);
            case "chest-partial" -> chestPartial(player);
            case "bundle", "bundle-move" -> bundleInInventory(player);
            case "bundle-chest" -> bundleInChest(player);
            case "filled-shulker" -> filledShulkerChest(player);
            case "placed-shulker" -> placedShulker(player);
            case "illegal-enchant" -> illegalSword(player);
            case "custom-item" -> customItem(player);
            case "clear-filler" -> clearFiller(player);
            case "death-diamonds" -> deathDiamonds(player);
            case "pickup-now" -> enableNearbyPickup(player);
            case "cross-server-kit" -> crossServerKit(player);
            case "chest-add-32" -> chestAdd(player, 32);
            case "teleport-spawn" -> teleportSpawn(player);
            case "add-diamonds-32" -> addDiamonds(player, 32);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    Map<String, Object> dropDiamonds(Player player, int amount) {
        return dropDiamonds(player, amount, true);
    }

    Map<String, Object> dropDiamonds(Player player, int amount, boolean reset) {
        if (reset) {
            fixture.resetPlayer(player, true);
            fixture.clearNearby(player);
        }
        Location dropAt = player.getLocation().clone().add(0, 0.15, 0);
        ItemStack stack = new ItemStack(Material.DIAMOND, amount);
        Item item = player.getWorld().dropItem(dropAt, stack);
        item.setPickupDelay(0);
        item.setCanMobPickup(false);
        item.setUnlimitedLifetime(true);
        return result(player, "drop-diamonds", Map.of(
                "amount", amount,
                "reset", reset,
                "x", dropAt.getX(),
                "y", dropAt.getY(),
                "z", dropAt.getZ()
        ));
    }

    Map<String, Object> fillChest(Player player, int amount) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, new ItemStack(Material.DIAMOND, amount));
        Location loc = fixture.chestLocation();
        return result(player, "chest", Map.of(
                "amount", amount,
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> fillChestMaterial(Player player, Material material, int amount) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        return putChest(player, material, amount);
    }

    Map<String, Object> putChest(Player player, Material material, int amount) {
        Inventory chest = fixture.chestInventory();
        chest.setItem(0, new ItemStack(material, amount));
        Location loc = fixture.chestLocation();
        return result(player, "chest-put", Map.of(
                "material", material.name(),
                "amount", amount,
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> chestNumberKey(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 1));
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, new ItemStack(Material.DIAMOND, 64));
        Location loc = fixture.chestLocation();
        return result(player, "chest-number-key", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> chestDoubleClick(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 16));
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, new ItemStack(Material.DIAMOND, 16));
        chest.setItem(1, new ItemStack(Material.DIAMOND, 16));
        chest.setItem(2, new ItemStack(Material.DIAMOND, 16));
        Location loc = fixture.chestLocation();
        return result(player, "chest-double-click", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> placedShulkerMaterial(Player player, Material material, int amount) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        return putPlacedShulker(player, material, amount);
    }

    Map<String, Object> putPlacedShulker(Player player, Material material, int amount) {
        var inventory = fixture.placedShulkerInventory();
        inventory.setItem(0, new ItemStack(material, amount));
        Location loc = fixture.placedShulkerLocation();
        return result(player, "placed-shulker-put", Map.of(
                "material", material.name(),
                "amount", amount,
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> clearInventory(Player player) {
        player.getInventory().clear();
        player.setItemOnCursor(null);
        return result(player, "clear-inventory", Map.of());
    }

    Map<String, Object> chestPartial(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 32));
        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 64);
        for (int slot = 1; slot < 36; slot++) {
            inventory.setItem(slot, cobble.clone());
        }
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, new ItemStack(Material.DIAMOND, 64));
        Location loc = fixture.chestLocation();
        return result(player, "chest-partial", Map.of(
                "amount", 64,
                "freeDiamondSpace", 32,
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> craft(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_BLOCK, 1));
        Location loc = fixture.craftingLocation();
        return result(player, "craft", Map.of(
                "recipe", "DIAMOND_BLOCK -> 9 DIAMOND",
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> craftShift(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_BLOCK, 3));
        Location loc = fixture.craftingLocation();
        return result(player, "craft-shift", Map.of(
                "recipe", "3 DIAMOND_BLOCK -> 27 DIAMOND",
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> furnaceOutput(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        var block = fixture.furnaceBlock();
        if (block.getState() instanceof org.bukkit.block.Furnace furnace) {
            furnace.getInventory().clear();
            furnace.getInventory().setResult(new ItemStack(Material.IRON_INGOT, 8));
            furnace.update(true, false);
        }
        Location loc = fixture.furnaceLocation();
        player.teleport(new Location(player.getWorld(), loc.getX() + 1.5, loc.getY(), loc.getZ() + 0.5, 90f, 0f));
        return result(player, "furnace-output", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> stonecutter(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.STONE, 8));
        Location loc = fixture.stonecutterLocation();
        player.teleport(new Location(player.getWorld(), loc.getX() - 1.5, loc.getY(), loc.getZ() + 0.5, -90f, 0f));
        return result(player, "stonecutter", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> merchant(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 1));
        Location loc = fixture.villagerLocation();
        Villager villager = player.getWorld().spawn(loc, Villager.class, spawned -> {
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setProfession(Villager.Profession.FARMER);
            spawned.setVillagerLevel(2);
            spawned.customName(Component.text("ItemGuardMerchant"));
            spawned.setCustomNameVisible(true);
            spawned.addScoreboardTag("itemguard-harness");
            MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.BREAD, 3), 32);
            recipe.addIngredient(new ItemStack(Material.EMERALD, 1));
            recipe.setExperienceReward(false);
            spawned.setRecipes(List.of(recipe));
        });
        return result(player, "merchant", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ(),
                "villagerUuid", villager.getUniqueId().toString()
        ));
    }

    Map<String, Object> bundleInInventory(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, filledBundle());
        return result(player, "bundle-move", Map.of("slot", 0));
    }

    Map<String, Object> bundleInChest(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, filledBundle());
        Location loc = fixture.chestLocation();
        return result(player, "bundle-chest", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> filledShulkerChest(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, filledShulker());
        Location loc = fixture.chestLocation();
        return result(player, "filled-shulker", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> placedShulker(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        var inventory = fixture.placedShulkerInventory();
        inventory.clear();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 64));
        Location loc = fixture.placedShulkerLocation();
        return result(player, "placed-shulker", Map.of(
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> illegalSword(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 255);
        player.getInventory().setItem(0, sword);
        return result(player, "illegal-enchant", Map.of("enchantment", "minecraft:sharpness", "level", 255));
    }

    Map<String, Object> customItem(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, legalCustomSword());
        return result(player, "custom-item", Map.of("material", "DIAMOND_SWORD"));
    }

    Map<String, Object> clearFiller(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == Material.COBBLESTONE) {
                inventory.setItem(slot, null);
            }
        }
        return result(player, "clear-filler", Map.of());
    }

    Map<String, Object> deathDiamonds(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 64));
        return result(player, "death-diamonds", Map.of("amount", 64));
    }

    Map<String, Object> crossServerKit(Player player) {
        fixture.resetPlayer(player, true);
        fixture.clearNearby(player);
        if (ItemGuardApiProvider.isAvailable()) {
            var api = ItemGuardApiProvider.get();
            api.recordExpectedGain(player.getUniqueId(), "DIAMOND", 64, "ItemGuard-TestHarness", "hs-kit");
            api.recordExpectedGain(player.getUniqueId(), "ELYTRA", 1, "ItemGuard-TestHarness", "hs-kit");
        }
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 64));
        player.getInventory().setChestplate(new ItemStack(Material.ELYTRA, 1));
        return result(player, "cross-server-kit", Map.of("DIAMOND", 64, "ELYTRA", 1));
    }

    Map<String, Object> teleportSpawn(Player player) {
        fixture.ensureReady();
        fixture.resetPlayer(player, false);
        Location spawn = fixture.spawn(player.getWorld() == null ? fixture.overworld() : player.getWorld());
        return result(player, "teleport-spawn", Map.of(
                "x", spawn.getX(),
                "y", spawn.getY(),
                "z", spawn.getZ()
        ));
    }

    Map<String, Object> chestAdd(Player player, int amount) {
        fixture.ensureReady();
        fixture.resetPlayer(player, false);
        Inventory chest = fixture.chestInventory();
        chest.clear();
        chest.setItem(0, new ItemStack(Material.DIAMOND, amount));
        Location loc = fixture.chestLocation();
        return result(player, "chest-add", Map.of(
                "amount", amount,
                "x", loc.getBlockX(),
                "y", loc.getBlockY(),
                "z", loc.getBlockZ()
        ));
    }

    Map<String, Object> addDiamonds(Player player, int amount) {
        fixture.ensureReady();
        if (ItemGuardApiProvider.isAvailable()) {
            ItemGuardApiProvider.get().recordExpectedGain(
                    player.getUniqueId(),
                    "DIAMOND",
                    amount,
                    "ItemGuard-TestHarness",
                    "hs-add"
            );
        }
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, amount));
        return result(player, "add-diamonds", Map.of("amount", amount));
    }

    Map<String, Object> enableNearbyPickup(Player player) {
        int items = 0;
        Location at = player.getLocation();
        for (var entity : player.getWorld().getNearbyEntities(at, 16, 12, 16)) {
            if (entity instanceof Item item) {
                item.setPickupDelay(0);
                item.teleport(at.clone().add(0, 0.1, 0));
                items++;
            }
        }
        return result(player, "pickup-now", Map.of("items", items));
    }

    Map<String, Object> kill(Player player) {
        player.setInvulnerable(false);
        player.setNoDamageTicks(0);
        try {
            player.setAbsorptionAmount(0);
        } catch (RuntimeException ignored) {
            // Older Paper builds may not expose absorption here.
        }
        fixture.rememberDeath(player.getLocation());
        player.damage(10_000.0);
        if (player.getHealth() > 0 && !player.isDead()) {
            player.setHealth(0.0);
        }
        return result(player, "kill", Map.of(
                "keepInventory", Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)),
                "dead", player.isDead() || player.getHealth() <= 0
        ));
    }

    Map<String, Object> scan(Player player) {
        boolean dispatched = plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "ig scan " + player.getName()
        );
        Map<String, Object> dump = plugin.diagnostics().dump(player);
        dump.put("scenario", "scan");
        dump.put("dispatched", dispatched);
        return dump;
    }

    Map<String, Object> setGameMode(Player player, GameMode mode) {
        player.setGameMode(mode);
        return result(player, "gamemode", Map.of("gamemode", mode.name()));
    }

    Map<String, Object> setKeepInventory(Player player, boolean keep) {
        player.getWorld().setGameRule(GameRule.KEEP_INVENTORY, keep);
        return result(player, "keep-inventory", Map.of("keepInventory", keep));
    }

    private Map<String, Object> result(Player player, String scenario, Map<String, Object> extra) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("scenario", scenario);
        map.put("player", player.getName());
        map.put("uuid", player.getUniqueId().toString());
        map.put("startedAt", Instant.now().toEpochMilli());
        map.put("world", player.getWorld().getName());
        map.put("x", player.getLocation().getX());
        map.put("y", player.getLocation().getY());
        map.put("z", player.getLocation().getZ());
        map.putAll(extra);
        return map;
    }

    private ItemStack filledBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.addItem(new ItemStack(Material.DIAMOND, 64));
            bundle.setItemMeta(bundleMeta);
        }
        return bundle;
    }

    private ItemStack filledShulker() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta)) {
            return shulker;
        }
        if (!(meta.getBlockState() instanceof ShulkerBox box)) {
            return shulker;
        }
        for (int slot = 0; slot < 27; slot++) {
            box.getInventory().setItem(slot, new ItemStack(Material.DIAMOND, 64));
        }
        meta.setBlockState(box);
        shulker.setItemMeta(meta);
        return shulker;
    }

    private ItemStack legalCustomSword() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("RPG Sword"));
        meta.lore(List.of(Component.text("A perfectly legal custom item")));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rpg"), PersistentDataType.STRING, "ok");
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey(plugin, "rpg-damage"),
                1.0,
                AttributeModifier.Operation.ADD_NUMBER
        );
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
        stack.setItemMeta(meta);
        try {
            ItemMeta again = stack.getItemMeta();
            again.setCustomModelData(12);
            stack.setItemMeta(again);
        } catch (RuntimeException ignored) {
            // Name/lore/PDC still exercise CUSTOM_METADATA.
        }
        return stack;
    }
}
