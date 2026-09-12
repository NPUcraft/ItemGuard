package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.session.PlayerGuardSession;
import com.npucraft.itemguard.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;

/**
 * Marks players dirty from events, then reconciles at most once after a short debounce.
 * A slower heartbeat pass covers mutations that did not raise an inventory event.
 */
public final class ReconciliationScheduler {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final ReconciliationService reconciliation;
    private volatile PluginSettings settings;
    private BukkitTask periodic;
    private BukkitTask retention;
    private long tick;

    public ReconciliationScheduler(
            Plugin plugin,
            SessionManager sessions,
            ReconciliationService reconciliation,
            PluginSettings settings
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.reconciliation = reconciliation;
        this.settings = settings;
    }

    public void start() {
        stop();
        long interval = Math.max(1L, settings.reconcileIntervalTicks());
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::periodic, interval, interval);
        retention = Bukkit.getScheduler().runTaskTimer(plugin, this::timeouts, 20L, 20L);
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
        start();
    }

    public void stop() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
        if (retention != null) {
            retention.cancel();
            retention = null;
        }
    }

    public void markDirty(Player player) {
        if (player == null || !player.isOnline() || !plugin.isEnabled()) {
            return;
        }
        PlayerGuardSession session = sessions.require(player, Instant.now());
        if (!session.takeReconcileSchedule()) {
            return;
        }
        long delay = Math.max(1L, settings.eventDebounceTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.releaseReconcileSchedule();
            if (!plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            reconciliation.reconcile(player, "event");
        }, delay);
    }

    private void periodic() {
        if (!plugin.isEnabled()) {
            return;
        }
        tick++;
        int dirty = 0;
        long interval = Math.max(1L, settings.reconcileIntervalTicks());
        long heartbeat = Math.max(0L, settings.heartbeatIntervalTicks());
        boolean heartbeatDue = isHeartbeatDue(tick, interval, heartbeat);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerGuardSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            if (session.dirty() || session.lastSnapshot() == null) {
                dirty++;
                reconciliation.reconcile(player, "periodic");
            } else if (heartbeatDue) {
                reconciliation.reconcile(player, "heartbeat");
            }
        }
        reconciliation.performance().setDirtyQueue(dirty);
    }

    /**
     * {@code heartbeat-interval-ticks} is game ticks, not periodic-task cycles.
     * With interval 20 and heartbeat 40, a silent pass runs every 2 seconds.
     */
    static boolean isHeartbeatDue(long periodicCount, long intervalTicks, long heartbeatTicks) {
        if (heartbeatTicks <= 0 || periodicCount <= 0) {
            return false;
        }
        long accumulatedTicks = periodicCount * Math.max(1L, intervalTicks);
        return accumulatedTicks % heartbeatTicks < Math.max(1L, intervalTicks);
    }

    private void timeouts() {
        if (!plugin.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        Duration timeout = settings.huskSyncTimeout();
        for (PlayerGuardSession session : sessions.all()) {
            if (!session.huskSync().isExpired(now, timeout)) {
                continue;
            }
            session.huskSync().timeout(now);
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                reconciliation.establishBaseline(player);
            }
            session.huskSync().resetToIdle();
        }
    }
}
