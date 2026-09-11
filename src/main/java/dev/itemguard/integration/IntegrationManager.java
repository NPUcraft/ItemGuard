package dev.itemguard.integration;

import dev.itemguard.config.IntegrationSettings;
import dev.itemguard.flow.InventorySnapshotService;
import dev.itemguard.flow.ReconciliationService;
import dev.itemguard.integration.husksync.HuskSyncIntegration;
import dev.itemguard.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntegrationManager {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final InventorySnapshotService snapshots;
    private final ReconciliationService reconciliation;
    private final Logger logger;
    private OptionalIntegration huskSync;

    public IntegrationManager(
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

    public void enable(IntegrationSettings settings) {
        close();
        if (!settings.huskSyncEnabled()) {
            logger.info("HuskSync Integration: disabled");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("HuskSync") == null) {
            logger.info("HuskSync Integration: disabled");
            return;
        }
        try {
            HuskSyncIntegration integration = new HuskSyncIntegration(plugin, sessions, snapshots, reconciliation, logger);
            integration.register();
            huskSync = integration;
            logger.info("HuskSync Integration: enabled");
        } catch (LinkageError | Exception exception) {
            logger.log(Level.WARNING, "Failed to enable HuskSync integration; ItemGuard will continue without it", exception);
            huskSync = null;
        }
    }

    public boolean huskSyncDetected() {
        return Bukkit.getPluginManager().getPlugin("HuskSync") != null;
    }

    public boolean huskSyncActive() {
        return huskSync != null && huskSync.active();
    }

    public void close() {
        if (huskSync != null) {
            huskSync.close();
            huskSync = null;
        }
    }
}
