package com.npucraft.itemguard.listener;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.flow.ExpectedFlowService;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.session.SessionManager;
import com.npucraft.itemguard.trace.TraceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates sessions and the first inventory baseline. HuskSync, when present, replaces join baseline after apply.
 */
public final class PlayerSessionListener implements Listener {

    private final SessionManager sessions;
    private final ReconciliationService reconciliation;
    private final ExpectedFlowService expectedFlows;
    private final TraceService traces;
    private final Supplier<PluginSettings> settings;
    private final Logger logger;
    private final BooleanSupplier huskSyncActive;
    private final Consumer<UUID> onSessionCleared;

    public PlayerSessionListener(
            SessionManager sessions,
            ReconciliationService reconciliation,
            ExpectedFlowService expectedFlows,
            TraceService traces,
            Supplier<PluginSettings> settings,
            Logger logger,
            BooleanSupplier huskSyncActive,
            Consumer<UUID> onSessionCleared
    ) {
        this.sessions = sessions;
        this.reconciliation = reconciliation;
        this.expectedFlows = expectedFlows;
        this.traces = traces;
        this.settings = settings;
        this.logger = logger;
        this.huskSyncActive = huskSyncActive;
        this.onSessionCleared = onSessionCleared;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            sessions.require(event.getPlayer(), Instant.now());
            if (!huskSyncActive.getAsBoolean()) {
                reconciliation.establishBaseline(event.getPlayer());
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to initialize session for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            UUID playerId = event.getPlayer().getUniqueId();
            expectedFlows.ledger().clear(playerId);
            sessions.remove(playerId);
            traces.dropPlayer(playerId, settings.get().keepTraceAfterQuit());
            traces.purgeExpired(Instant.now());
            if (onSessionCleared != null) {
                onSessionCleared.accept(playerId);
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to cleanup session for " + event.getPlayer().getUniqueId(), exception);
        }
    }
}
