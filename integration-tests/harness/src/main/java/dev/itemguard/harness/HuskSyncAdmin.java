package dev.itemguard.harness;

import net.william278.husksync.api.BukkitHuskSyncAPI;
import net.william278.husksync.data.DataSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Invokes official HuskSync commands/API. Does not mutate ItemGuard state.
 */
public final class HuskSyncAdmin {

    private final ItemGuardTestHarnessPlugin plugin;

    public HuskSyncAdmin(ItemGuardTestHarnessPlugin plugin) {
        this.plugin = plugin;
    }

    Map<String, Object> save(Player player) {
        String command = "userdata save " + player.getName();
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            command = "husksync userdata save " + player.getName();
            dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (!dispatched) {
            throw new IllegalStateException("HuskSync userdata save command was not dispatched");
        }
        return Map.of("ok", true, "command", command);
    }

    Map<String, Object> restore(Player player, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        String command = "userdata restore " + player.getName() + " " + snapshotId;
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            command = "husksync userdata restore " + player.getName() + " " + snapshotId;
            dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (!dispatched) {
            throw new IllegalStateException("HuskSync userdata restore command was not dispatched");
        }
        return Map.of("ok", true, "command", command);
    }

    Map<String, Object> list(Player player) {
        try {
            BukkitHuskSyncAPI api = BukkitHuskSyncAPI.getInstance();
            if (api == null) {
                throw new IllegalStateException("BukkitHuskSyncAPI.getInstance() is null");
            }
            var user = api.getUser(player.getUniqueId()).get(8, TimeUnit.SECONDS)
                    .orElseThrow(() -> new IllegalStateException("HuskSync has no user for " + player.getUniqueId()));
            List<DataSnapshot.Unpacked> snapshots = api.getSnapshots(user).get(8, TimeUnit.SECONDS);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (DataSnapshot.Unpacked snapshot : snapshots) {
                Map<String, Object> row = new LinkedHashMap<>();
                UUID id = snapshot.getId();
                row.put("id", id == null ? null : id.toString());
                row.put("shortId", snapshot.getShortId());
                row.put("saveCause", snapshot.getSaveCause() == null ? null : snapshot.getSaveCause().name());
                row.put("timestamp", snapshot.getTimestamp() == null ? null : snapshot.getTimestamp().toString());
                row.put("serverName", snapshot.getServerName());
                rows.add(row);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ok", true);
            map.put("snapshots", rows);
            return map;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "HuskSync snapshot list failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    exception
            );
        }
    }
}
