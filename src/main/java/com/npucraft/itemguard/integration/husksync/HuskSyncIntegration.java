package com.npucraft.itemguard.integration.husksync;

import com.npucraft.itemguard.flow.InventorySnapshotService;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.integration.OptionalIntegration;
import com.npucraft.itemguard.session.PlayerGuardSession;
import com.npucraft.itemguard.session.SessionManager;
import net.william278.husksync.event.BukkitPreSyncEvent;
import net.william278.husksync.event.BukkitSyncCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HuskSync apply is an explainable source ({@code HUSKSYNC_DATA_APPLY}), not an automatic safety verdict.
 * After apply, the post-sync inventory becomes the new reconciliation baseline so the same items are not
 * treated as a later unexplained gain.
 */
public final class HuskSyncIntegration implements OptionalIntegration, Listener {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final InventorySnapshotService snapshots;
    private final ReconciliationService reconciliation;
    private final Logger logger;
    private boolean registered;

    public HuskSyncIntegration(
            Plugin plugin,
            SessionManager sessions,
            InventorySnapshotService snapshots,
            ReconciliationService reconciliation,
            Logger logger
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.reconciliation = reconciliation;
        this.logger = logger;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    @Override
    public String name() {
        return "HuskSync";
    }

    @Override
    public boolean active() {
        return registered;
    }

    @Override
    public void close() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreSync(BukkitPreSyncEvent event) {
        Player player = playerOf(event.getUser().getUuid());
        if (player == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            PlayerGuardSession session = sessions.require(player, now);
            InventorySnapshot before = snapshots.capture(player, now);
            session.setLastSnapshot(before);
            session.huskSync().begin(before, now, UUID.randomUUID());
        } catch (LinkageError | Exception exception) {
            logger.log(Level.WARNING, "HuskSync PreSync handling failed for " + event.getUser().getUuid()
                    + "; ItemGuard will continue without this apply", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSyncComplete(BukkitSyncCompleteEvent event) {
        Player player = playerOf(event.getUser().getUuid());
        if (player == null) {
            return;
        }
        try {
            PlayerGuardSession session = sessions.require(player, Instant.now());
            InventorySnapshot before = session.huskSync().snapshotBefore();
            UUID oldest = session.huskSync().oldestOpenId();
            boolean done = session.huskSync().complete(oldest, Instant.now());
            if (done) {
                reconciliation.applyHuskSync(player, before);
                session.huskSync().resetToIdle();
                return;
            }
            if (!session.huskSync().isSyncing()) {
                reconciliation.applyHuskSync(player, session.lastSnapshot());
            }
        } catch (LinkageError | Exception exception) {
            logger.log(Level.WARNING, "HuskSync SyncComplete handling failed for " + event.getUser().getUuid()
                    + "; ItemGuard will re-baseline instead of treating this as UNKNOWN", exception);
            PlayerGuardSession session = sessions.get(event.getUser().getUuid());
            if (session != null) {
                session.huskSync().fail("sync-complete-error", Instant.now());
                session.huskSync().resetToIdle();
            }
            reconciliation.establishBaseline(player);
        }
    }

    private static Player playerOf(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }
}
