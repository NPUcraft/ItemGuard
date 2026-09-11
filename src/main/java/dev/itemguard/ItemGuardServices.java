package dev.itemguard;

import dev.itemguard.alert.AlertService;
import dev.itemguard.api.ItemGuardApiImpl;
import dev.itemguard.api.ItemGuardApiProvider;
import dev.itemguard.api.ItemGuardDiagnosticsImpl;
import dev.itemguard.command.ItemGuardCommand;
import dev.itemguard.config.ConfigManager;
import dev.itemguard.flow.ExpectedFlowLedger;
import dev.itemguard.flow.ExpectedFlowService;
import dev.itemguard.flow.InventorySnapshotService;
import dev.itemguard.flow.ReconciliationScheduler;
import dev.itemguard.flow.ReconciliationService;
import dev.itemguard.flow.UnknownGainCorrelator;
import dev.itemguard.integration.IntegrationManager;
import dev.itemguard.item.ItemSignatures;
import dev.itemguard.listener.CraftingListener;
import dev.itemguard.listener.DeathListener;
import dev.itemguard.listener.InventoryInteractionListener;
import dev.itemguard.listener.InventorySlotChangeListener;
import dev.itemguard.listener.PickupDropListener;
import dev.itemguard.listener.PlayerSessionListener;
import dev.itemguard.log.ForensicLogPublisher;
import dev.itemguard.log.ForensicLogService;
import dev.itemguard.log.ForensicLogSink;
import dev.itemguard.log.NoOpForensicLogSink;
import dev.itemguard.risk.RiskEngine;
import dev.itemguard.risk.detector.BurstDetector;
import dev.itemguard.risk.detector.RiskDetector;
import dev.itemguard.risk.detector.ShulkerFlowDetector;
import dev.itemguard.risk.detector.UnexplainedGainDetector;
import dev.itemguard.scan.IllegalItemScanner;
import dev.itemguard.session.SessionManager;
import dev.itemguard.trace.TraceService;
import dev.itemguard.util.PerformanceStats;
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
        this.unexplainedGainDetector = new UnexplainedGainDetector(config.riskSettings());
        this.shulkerFlowDetector = new ShulkerFlowDetector(config.riskSettings());
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
                integrations::huskSyncActive
        ), plugin);
        Bukkit.getPluginManager().registerEvents(new PickupDropListener(expectedFlows, scheduler, plugin.getLogger()), plugin);
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
        unexplainedGainDetector.updateSettings(config.riskSettings());
        shulkerFlowDetector.updateSettings(config.riskSettings());
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
