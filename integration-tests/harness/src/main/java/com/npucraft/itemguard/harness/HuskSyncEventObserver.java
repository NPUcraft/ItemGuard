package com.npucraft.itemguard.harness;

import net.william278.husksync.event.BukkitDataSaveEvent;
import net.william278.husksync.event.BukkitPreSyncEvent;
import net.william278.husksync.event.BukkitSyncCompleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observes official HuskSync Bukkit events. Never cancels or mutates them.
 */
public final class HuskSyncEventObserver implements Listener {

    private final ItemGuardTestHarnessPlugin plugin;

    public HuskSyncEventObserver(ItemGuardTestHarnessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreSync(BukkitPreSyncEvent event) {
        Map<String, Object> map = base("BukkitPreSyncEvent", event.getUser().getUuid().toString(), event.getUser().getName());
        plugin.eventLog().record(map);
        plugin.getLogger().info("ITEMGUARD_HUSKSYNC_EVENT " + ItemGuardTestHarnessPlugin.GSON.toJson(map));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSyncComplete(BukkitSyncCompleteEvent event) {
        Map<String, Object> map = base("BukkitSyncCompleteEvent", event.getUser().getUuid().toString(), event.getUser().getName());
        plugin.eventLog().record(map);
        plugin.getLogger().info("ITEMGUARD_HUSKSYNC_EVENT " + ItemGuardTestHarnessPlugin.GSON.toJson(map));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDataSave(BukkitDataSaveEvent event) {
        Map<String, Object> map = base("BukkitDataSaveEvent", event.getUser().getUuid().toString(), event.getUser().getName());
        try {
            var cause = event.getSaveCause();
            map.put("saveCause", cause == null ? null : cause.name());
        } catch (RuntimeException ignored) {
            map.put("saveCause", null);
        }
        plugin.eventLog().record(map);
        plugin.getLogger().info("ITEMGUARD_HUSKSYNC_EVENT " + ItemGuardTestHarnessPlugin.GSON.toJson(map));
    }

    private Map<String, Object> base(String type, String uuid, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("timestampEpochMilli", Instant.now().toEpochMilli());
        map.put("serverId", plugin.serverId());
        map.put("uuid", uuid);
        map.put("player", name);
        return map;
    }
}
