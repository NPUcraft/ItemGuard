package com.npucraft.itemguard.harness;

import com.npucraft.itemguard.api.ItemGuardApiProvider;
import com.npucraft.itemguard.api.PlayerDiagnosticSnapshot;
import com.npucraft.itemguard.api.ScanFindingView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiagnosticExporter {

    private final ItemGuardTestHarnessPlugin plugin;

    DiagnosticExporter(ItemGuardTestHarnessPlugin plugin) {
        this.plugin = plugin;
    }

    Map<String, Object> dump(Player player) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("player", player.getName());
        map.put("uuid", player.getUniqueId().toString());
        map.put("online", player.isOnline());
        map.put("gamemode", player.getGameMode().name());
        map.put("health", player.getHealth());
        map.put("food", player.getFoodLevel());
        map.put("op", player.isOp());
        map.put("world", player.getWorld().getName());
        map.put("x", player.getLocation().getBlockX());
        map.put("y", player.getLocation().getBlockY());
        map.put("z", player.getLocation().getBlockZ());
        map.put("inventory", inventoryCounts(player));
        map.put("slots", inventorySlots(player));
        map.put("kicked", false);
        map.put("banned", ((OfflinePlayer) player).isBanned());
        if (!ItemGuardApiProvider.isAvailable()) {
            map.put("itemguardAvailable", false);
            map.put("error", "ItemGuard API is not available");
            return map;
        }
        map.put("itemguardAvailable", true);
        PlayerDiagnosticSnapshot snapshot = ItemGuardApiProvider.get().diagnostics().player(player.getUniqueId());
        List<ScanFindingView> scan = ItemGuardApiProvider.get().diagnostics().scan(player.getUniqueId());
        map.put("snapshot", snapshot);
        map.put("scan", scan);
        map.put("serverId", plugin.serverId());
        map.put("huskSyncEvents", plugin.eventLog().snapshot());
        writeState(map);
        return map;
    }

    Map<String, Object> environment() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("minecraft", Bukkit.getMinecraftVersion());
        map.put("bukkitVersion", Bukkit.getVersion());
        map.put("java", System.getProperty("java.version"));
        Plugin itemGuard = Bukkit.getPluginManager().getPlugin("ItemGuard");
        map.put("itemguard", itemGuard == null ? null : itemGuard.getPluginMeta().getVersion());
        map.put("itemguardEnabled", itemGuard != null && itemGuard.isEnabled());
        Plugin harness = Bukkit.getPluginManager().getPlugin("ItemGuard-TestHarness");
        map.put("harness", harness == null ? null : harness.getPluginMeta().getVersion());
        map.put("onlinePlayers", Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        map.put("fixtureReady", plugin.fixture().ready());
        map.put("serverId", plugin.serverId());
        Plugin huskSync = Bukkit.getPluginManager().getPlugin("HuskSync");
        map.put("huskSyncDetected", huskSync != null);
        map.put("huskSyncEnabled", huskSync != null && huskSync.isEnabled());
        map.put("huskSyncVersion", huskSync == null ? null : huskSync.getPluginMeta().getVersion());
        boolean integrationActive = false;
        boolean detectedByItemGuard = false;
        if (ItemGuardApiProvider.isAvailable()) {
            var diagnostics = ItemGuardApiProvider.get().diagnostics();
            integrationActive = diagnostics.huskSyncActive();
            detectedByItemGuard = diagnostics.huskSyncDetected();
        }
        map.put("huskSyncDetectedByItemGuard", detectedByItemGuard);
        map.put("huskSyncIntegrationActive", integrationActive);
        map.put("runtimeSettings", runtimeSettings());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runtimeSettings() {
        if (!ItemGuardApiProvider.isAvailable()) {
            return Map.of();
        }
        try {
            Object diagnostics = ItemGuardApiProvider.get().diagnostics();
            var method = diagnostics.getClass().getMethod("runtimeSettings");
            Object value = method.invoke(diagnostics);
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> copy = new LinkedHashMap<>();
                raw.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
                return copy;
            }
        } catch (ReflectiveOperationException ignored) {
            // RC1 ItemGuard has no runtimeSettings(); upgrade tests only read this after RC2.
        }
        return Map.of();
    }

    Map<String, Object> identify(String playerName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("serverId", plugin.serverId());
        Player player = playerName == null ? null : Bukkit.getPlayerExact(playerName);
        if (player != null) {
            map.put("player", player.getName());
            map.put("uuid", player.getUniqueId().toString());
            map.put("online", true);
        } else {
            map.put("online", false);
            map.put("onlinePlayers", Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return map;
    }

    Map<String, Integer> inventoryCounts(Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        addCounts(counts, inventory.getStorageContents());
        addCounts(counts, inventory.getArmorContents());
        addCounts(counts, inventory.getExtraContents());
        addCounts(counts, new ItemStack[]{player.getItemOnCursor()});
        return counts;
    }

    private static void addCounts(Map<String, Integer> counts, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            counts.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
        }
    }

    private static List<Map<String, Object>> inventorySlots(Player player) {
        List<Map<String, Object>> slots = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (storage == null) {
            return slots;
        }
        for (int index = 0; index < storage.length; index++) {
            ItemStack stack = storage[index];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("slot", index);
            slot.put("material", stack.getType().name());
            slot.put("amount", stack.getAmount());
            slots.add(slot);
        }
        return slots;
    }

    private void writeState(Map<String, Object> map) {
        try {
            Path dir = plugin.reportsDir();
            Files.createDirectories(dir);
            String serverId = plugin.serverId();
            Path file = dir.resolve("integration".equals(serverId) ? "state.json" : "state-" + serverId + ".json");
            Files.writeString(file, ItemGuardTestHarnessPlugin.GSON.toJson(map), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to write state.json: " + exception.getMessage());
        }
    }
}
