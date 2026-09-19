package com.npucraft.itemguard;

import com.npucraft.itemguard.alert.AlertService;
import com.npucraft.itemguard.api.ItemGuardApiImpl;
import com.npucraft.itemguard.api.ItemGuardApiProvider;
import com.npucraft.itemguard.api.ItemGuardDiagnosticsImpl;
import com.npucraft.itemguard.command.ItemGuardCommand;
import com.npucraft.itemguard.config.ConfigManager;
import com.npucraft.itemguard.flow.ExpectedFlowLedger;
import com.npucraft.itemguard.flow.ExpectedFlowService;
import com.npucraft.itemguard.flow.InventorySnapshotService;
import com.npucraft.itemguard.flow.ReconciliationScheduler;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.flow.UnknownGainCorrelator;
import com.npucraft.itemguard.integration.IntegrationManager;
import com.npucraft.itemguard.item.ItemSignatures;
import com.npucraft.itemguard.listener.CraftingListener;
import com.npucraft.itemguard.listener.DeathListener;
import com.npucraft.itemguard.listener.InventoryInteractionListener;
import com.npucraft.itemguard.listener.InventorySlotChangeListener;
import com.npucraft.itemguard.listener.PickupDropListener;
import com.npucraft.itemguard.listener.PlayerSessionListener;
import com.npucraft.itemguard.listener.VanillaBucketListener;
import com.npucraft.itemguard.log.ForensicLogPublisher;
import com.npucraft.itemguard.log.ForensicLogService;
import com.npucraft.itemguard.log.ForensicLogSink;
import com.npucraft.itemguard.log.NoOpForensicLogSink;
import com.npucraft.itemguard.risk.RiskEngine;
import com.npucraft.itemguard.risk.detector.BurstDetector;
import com.npucraft.itemguard.risk.detector.RiskDetector;
import com.npucraft.itemguard.risk.detector.ShulkerFlowDetector;
import com.npucraft.itemguard.risk.detector.UnexplainedGainDetector;
import com.npucraft.itemguard.scan.IllegalItemScanner;
import com.npucraft.itemguard.session.SessionManager;
import com.npucraft.itemguard.trace.TraceService;
import com.npucraft.itemguard.util.PerformanceStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;

import java.util.List;
import java.util.logging.Logger;

public final class ItemGuardServices {

    private final ItemGuardPlugin plugin;
    private final ConfigManager config;
    private final SessionManager sessions;
    private final InventorySnapshotService snapshots;
    private final ExpectedFlowService expectedFlows;
    private final TraceService traces;
    private final RiskEngine riskEngine;
    private final IllegalItemScanner scanner;
    private final BurstDetector burstDetector;
    private final UnexplainedGainDetector unexplainedGainDetector;
    private final ShulkerFlowDetector shulkerFlowDetector;
    private final ReconciliationService reconciliation;
    private final ReconciliationScheduler scheduler;
    private final AlertService alerts;
    private final IntegrationManager integrations;
    private final ItemGuardApiImpl api;
    private final PerformanceStats performance;
    private final ForensicLogPublisher forensicPublisher;
    private ForensicLogSink forensic;

    public ItemGuardServices(ItemGuardPlugin plugin) {
        this.plugin = plugin;
        Logger logger = plugin.getLogger();
        this.config = new ConfigManager(plugin);
        config.load();

        this.forensic = ForensicLogService.create(plugin.getDataFolder().toPath(), config.loggingSettings(), logger);
        this.forensicPublisher = new ForensicLogPublisher(config, forensic);

        ItemSignatures signatures = new ItemSignatures(logger);
        this.sessions = new SessionManager();
        this.snapshots = new InventorySnapshotService(signatures, config.itemValues(), config.pluginSettings());
        this.expectedFlows = new ExpectedFlowService(new ExpectedFlowLedger(), config.pluginSettings());
        this.traces = new TraceService(config.pluginSettings());
        this.riskEngine = new RiskEngine(config.riskSettings());
        this.scanner = new IllegalItemScanner(signatures, logger, config.scannerSettings(), config.riskSettings());
        this.burstDetector = new BurstDetector(config.riskSettings(), config.itemValues());
        this.unexplainedGainDetector = new UnexplainedGainDetector(config.riskSettings(), config.itemValues());
        this.shulkerFlowDetector = new ShulkerFlowDetector(config.riskSettings(), config.itemValues());
        List<RiskDetector> detectors = List.of(
                unexplainedGainDetector,
                burstDetector,
                shulkerFlowDetector
        );
        this.alerts = new AlertService(config, logger, forensicPublisher);
        this.performance = new PerformanceStats();
        this.reconciliation = new ReconciliationService(
                sessions,
                snapshots,
                expectedFlows,
                new UnknownGainCorrelator(),
                traces,
                riskEngine,
                detectors,
                burstDetector,
                scanner,
                logger,
                result -> {
                    alerts.onReconciliation(result);
                    forensicPublisher.onReconciliation(result);
                },
                performance,
                config.pluginSettings(),
                config.riskSettings()
        );
        this.reconciliation.setFollowUp(player -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            reconciliation.reconcile(player, "attribution-grace");
        }, Math.max(1L, config.pluginSettings().attributionGraceTicks())));
        this.scheduler = new ReconciliationScheduler(plugin, sessions, reconciliation, config.pluginSettings());
        this.integrations = new IntegrationManager(plugin, sessions, snapshots, reconciliation, logger);
        this.api = new ItemGuardApiImpl(
                expectedFlows,
                scheduler,
                new ItemGuardDiagnosticsImpl(sessions, traces, expectedFlows, scanner, alerts, config, integrations)
        );
    }

    public void enable() {
        integrations.enable(config.integrationSettings());
        Bukkit.getPluginManager().registerEvents(new PlayerSessionListener(
                sessions,
                reconciliation,
                expectedFlows,
                traces,
                config::pluginSettings,
                plugin.getLogger(),
                integrations::huskSyncActive,
                playerId -> {
                    riskEngine.clearPlayer(playerId);
                    scanner.clearPlayer(playerId);
                }
        ), plugin);
        Bukkit.getPluginManager().registerEvents(new PickupDropListener(expectedFlows, scheduler, plugin.getLogger()), plugin);
        Bukkit.getPluginManager().registerEvents(new VanillaBucketListener(expectedFlows, scheduler, plugin.getLogger()), plugin);
        Bukkit.getPluginManager().registerEvents(new InventoryInteractionListener(expectedFlows, scheduler, sessions, plugin.getLogger()), plugin);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(expectedFlows, scheduler, plugin.getLogger()), plugin);
        Bukkit.getPluginManager().registerEvents(new DeathListener(reconciliation, scheduler, plugin.getLogger()), plugin);
        Bukkit.getPluginManager().registerEvents(new InventorySlotChangeListener(scheduler, plugin.getLogger()), plugin);

        ItemGuardCommand command = new ItemGuardCommand(
                plugin,
                config,
                sessions,
                traces,
                expectedFlows,
                reconciliation,
                scanner,
                integrations,
                alerts::recent,
                this::reload,
                forensicPublisher
        );
        PluginCommand root = plugin.getCommand("itemguard");
        if (root != null) {
            root.setExecutor(command);
            root.setTabCompleter(command);
        } else {
            plugin.getLogger().severe("Command 'itemguard' is missing from plugin.yml");
        }

        ItemGuardApiProvider.register(api);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                reconciliation.establishBaseline(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to baseline online player " + player.getUniqueId(), exception);
            }
        }
        scheduler.start();
        logStartup();
    }

    public void disable() {
        scheduler.stop();
        integrations.close();
        ItemGuardApiProvider.unregister();
        sessions.clear();
        traces.clear();
        riskEngine.clearAll();
        if (forensic != null) {
            forensic.shutdown(config.loggingSettings().shutdownTimeout());
            forensic = NoOpForensicLogSink.INSTANCE;
            forensicPublisher.updateSink(forensic);
        }
    }

    public void reload() {
        config.reload();
        snapshots.updateSettings(config.pluginSettings());
        expectedFlows.updateSettings(config.pluginSettings());
        traces.updateSettings(config.pluginSettings());
        scanner.updateSettings(config.scannerSettings(), config.riskSettings());
        riskEngine.updateSettings(config.riskSettings());
        burstDetector.updateSettings(config.riskSettings(), config.itemValues());
        unexplainedGainDetector.updateSettings(config.riskSettings(), config.itemValues());
        shulkerFlowDetector.updateSettings(config.riskSettings(), config.itemValues());
        reconciliation.updateSettings(config.pluginSettings(), config.riskSettings());
        alerts.rebuildAggregator();
        scheduler.updateSettings(config.pluginSettings());
        integrations.enable(config.integrationSettings());
        var nextLogging = config.loggingSettings();
        if (!forensic.enabled() && nextLogging.enabled()) {
            forensic = ForensicLogService.create(plugin.getDataFolder().toPath(), nextLogging, plugin.getLogger());
        } else {
            forensic = forensic.applySettings(nextLogging);
        }
        forensicPublisher.updateSink(forensic);
    }

    private void logStartup() {
        plugin.getLogger().info("ItemGuard v" + plugin.getPluginMeta().getVersion());
        plugin.getLogger().info("Paper " + Bukkit.getMinecraftVersion() + " detected");
        plugin.getLogger().info("Illegal Item Scanner: " + (config.pluginSettings().scannerEnabled() ? "enabled" : "disabled"));
        plugin.getLogger().info("Item Flow Monitor: enabled");
        plugin.getLogger().info("Risk Engine: enabled");
        plugin.getLogger().info("HuskSync Integration: " + (integrations.huskSyncActive() ? "enabled" : "disabled"));
        plugin.getLogger().info("Forensic Logging: " + (forensic.enabled() ? "enabled" : "disabled"));
    }
}
