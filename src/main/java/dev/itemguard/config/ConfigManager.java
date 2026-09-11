package dev.itemguard.config;

import dev.itemguard.command.DurationParser;
import dev.itemguard.item.ItemValueRegistry;
import dev.itemguard.risk.model.SignalType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class ConfigManager {

    public static final List<String> BUNDLED_FILES = ConfigResourcePolicy.BUNDLED_FILES;

    /**
     * Existing operator files are never replaced. Only a missing file is copied from the JAR.
     */
    public static boolean shouldInstallBundledResource(File destination) {
        return ConfigResourcePolicy.shouldInstallBundledResource(destination);
    }

    private final JavaPlugin plugin;
    private PluginSettings pluginSettings = PluginSettings.defaults();
    private ScannerSettings scannerSettings = ScannerSettings.defaults();
    private RiskSettings riskSettings = RiskSettings.defaults();
    private AlertSettings alertSettings = AlertSettings.defaults();
    private IntegrationSettings integrationSettings = IntegrationSettings.defaults();
    private LoggingSettings loggingSettings = LoggingSettings.defaults(java.nio.file.Path.of("."));
    private ItemValueRegistry itemValues = new ItemValueRegistry(1, Map.of());
    private YamlConfiguration messages = new YamlConfiguration();
    private boolean lastReloadHadWarnings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        for (String file : ConfigResourcePolicy.BUNDLED_FILES) {
            File destination = new File(plugin.getDataFolder(), file);
            if (ConfigResourcePolicy.shouldInstallBundledResource(destination)) {
                plugin.saveResource(file, false);
            }
        }
        reload();
    }

    public void reload() {
        ConfigValidator validator = new ConfigValidator();
        try {
            pluginSettings = readPluginSettings(yaml("config.yml"), validator);
            scannerSettings = readScannerSettings(yaml("scanner.yml"), validator);
            riskSettings = readRiskSettings(yaml("risk.yml"), validator);
            alertSettings = readAlertSettings(yaml("alerts.yml"), validator);
            integrationSettings = readIntegrationSettings(yaml("integrations.yml"), validator);
            loggingSettings = readLoggingSettings(yaml("logging.yml"), validator, plugin.getDataFolder().toPath());
            itemValues = readItemValues(yaml("items.yml"), validator);
            messages = yaml("messages.yml");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to read ItemGuard config; keeping safe defaults where needed", exception);
        }
        validator.logTo(plugin.getLogger());
        lastReloadHadWarnings = validator.hasWarnings();
    }

    public PluginSettings pluginSettings() {
        return pluginSettings;
    }

    public ScannerSettings scannerSettings() {
        return scannerSettings;
    }

    public RiskSettings riskSettings() {
        return riskSettings;
    }

    public AlertSettings alertSettings() {
        return alertSettings;
    }

    public IntegrationSettings integrationSettings() {
        return integrationSettings;
    }

    public LoggingSettings loggingSettings() {
        return loggingSettings;
    }

    public ItemValueRegistry itemValues() {
        return itemValues;
    }

    public YamlConfiguration messages() {
        return messages;
    }

    public boolean lastReloadHadWarnings() {
        return lastReloadHadWarnings;
    }

    public String message(String path, String fallback) {
        String value = messages.getString(path);
        return value == null || value.isBlank() ? fallback : value;
    }

    private YamlConfiguration yaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (file.isFile() && file.length() > 0 && loaded.getKeys(false).isEmpty() && hasNonCommentContent(file)) {
            plugin.getLogger().warning("[ItemGuard] " + name + " could not be parsed. Using in-memory defaults. The file was not overwritten.");
        }
        return loaded;
    }

    private static boolean hasNonCommentContent(File file) {
        try {
            return Files.readAllLines(file.toPath()).stream()
                    .map(String::trim)
                    .anyMatch(line -> !line.isEmpty() && !line.startsWith("#"));
        } catch (IOException ignored) {
            return false;
        }
    }

    private PluginSettings readPluginSettings(YamlConfiguration yaml, ConfigValidator validator) {
        PluginSettings defaults = PluginSettings.defaults();
        return new PluginSettings(
                validator.bool("debug", yaml.get("debug"), defaults.debug()),
                validator.positiveInt("reconciliation.interval-ticks", yaml.getInt("reconciliation.interval-ticks", (int) defaults.reconcileIntervalTicks()), (int) defaults.reconcileIntervalTicks()),
                validator.positiveLong("reconciliation.expected-flow-ttl-millis", yaml.getLong("reconciliation.expected-flow-ttl-millis", defaults.expectedFlowTtlMillis()), defaults.expectedFlowTtlMillis()),
                validator.positiveInt("reconciliation.event-debounce-ticks", yaml.getInt("reconciliation.event-debounce-ticks", (int) defaults.eventDebounceTicks()), (int) defaults.eventDebounceTicks()),
                duration(yaml, "trace.retention-minutes", defaults.traceRetention(), true, validator),
                validator.positiveInt("trace.max-events-per-player", yaml.getInt("trace.max-events-per-player", defaults.maxTraceEventsPerPlayer()), defaults.maxTraceEventsPerPlayer()),
                validator.positiveInt("trace.max-total-events", yaml.getInt("trace.max-total-events", defaults.maxTotalTraceEvents()), defaults.maxTotalTraceEvents()),
                parseDuration(yaml.getString("trace.max-query-duration"), defaults.maxTraceQuery(), "trace.max-query-duration", validator),
                validator.clampInt("performance.signature-min-value", yaml.getInt("performance.signature-min-value", defaults.signatureMinValue()), 0, 100, defaults.signatureMinValue()),
                validator.bool("performance.hash-custom-items", yaml.get("performance.hash-custom-items"), defaults.hashCustomItems()),
                validator.positiveLong("performance.scan-debounce-millis", yaml.getLong("performance.scan-debounce-millis", defaults.scanDebounceMillis()), defaults.scanDebounceMillis()),
                validator.positiveInt("performance.max-scan-depth", yaml.getInt("performance.max-scan-depth", defaults.maxScanDepth()), defaults.maxScanDepth()),
                Duration.ofMillis(validator.positiveLong("session.husksync-timeout-millis", yaml.getLong("session.husksync-timeout-millis", defaults.huskSyncTimeout().toMillis()), defaults.huskSyncTimeout().toMillis())),
                validator.bool("session.keep-trace-after-quit", yaml.get("session.keep-trace-after-quit"), defaults.keepTraceAfterQuit()),
                validator.bool("scanner.enabled", yaml.get("scanner.enabled"), defaults.scannerEnabled()),
                validator.bool("scanner.auto-remove", yaml.get("scanner.auto-remove"), false),
                validator.bool("reconciliation.ignore-creative-inventory-gains", yaml.get("reconciliation.ignore-creative-inventory-gains"), defaults.ignoreCreativeInventoryGains()),
                validator.clampInt("reconciliation.heartbeat-interval-ticks", yaml.getInt("reconciliation.heartbeat-interval-ticks", (int) defaults.heartbeatIntervalTicks()), 0, 1200, (int) defaults.heartbeatIntervalTicks())
        );
    }

    private ScannerSettings readScannerSettings(YamlConfiguration yaml, ConfigValidator validator) {
        ScannerSettings defaults = ScannerSettings.defaults();
        return new ScannerSettings(
                validator.bool("enabled", yaml.get("enabled"), defaults.enabled()),
                validator.bool("auto-remove", yaml.get("auto-remove"), false),
                set(yaml, "ignored-namespaces", defaults.ignoredNamespaces()),
                validator.bool("enchantments.enabled", yaml.get("enchantments.enabled"), defaults.enchantmentsEnabled()),
                validator.bool("enchantments.flag-over-level", yaml.get("enchantments.flag-over-level"), defaults.flagOverLevel()),
                validator.bool("enchantments.flag-conflicting", yaml.get("enchantments.flag-conflicting"), defaults.flagConflicting()),
                yaml.getString("enchantments.conflicting-severity", defaults.conflictingSeverity()),
                validator.bool("stack.enabled", yaml.get("stack.enabled"), defaults.stackEnabled()),
                validator.bool("stack.flag-oversized", yaml.get("stack.flag-oversized"), defaults.flagOversized()),
                validator.bool("attributes.enabled", yaml.get("attributes.enabled"), defaults.attributesEnabled()),
                validator.clampDouble("attributes.maximum-absolute-amount", yaml.getDouble("attributes.maximum-absolute-amount", defaults.attributeMaxAbsolute()), 0.0, 1_000_000.0, defaults.attributeMaxAbsolute()),
                set(yaml, "attributes.allowed-operations", defaults.allowedAttributeOperations()),
                set(yaml, "attributes.ignored-materials", defaults.ignoredAttributeMaterials()),
                set(yaml, "attributes.ignored-namespaces", defaults.ignoredAttributeNamespaces()),
                yaml.getString("attributes.default-severity", defaults.attributeSeverity()),
                validator.bool("durability.enabled", yaml.get("durability.enabled"), defaults.durabilityEnabled()),
                validator.bool("durability.flag-negative", yaml.get("durability.flag-negative"), defaults.flagNegativeDurability()),
                validator.bool("durability.flag-above-max", yaml.get("durability.flag-above-max"), defaults.flagAboveMaxDurability()),
                validator.bool("components.enabled", yaml.get("components.enabled"), defaults.componentsEnabled()),
                validator.bool("components.flag-overridden", yaml.get("components.flag-overridden"), defaults.flagOverriddenComponents()),
                set(yaml, "components.suspicious-if-overridden", defaults.suspiciousComponents()),
                set(yaml, "components.custom-if-overridden", defaults.customComponents()),
                validator.bool("persistent-data.enabled", yaml.get("persistent-data.enabled"), defaults.persistentDataEnabled()),
                set(yaml, "persistent-data.denied-namespaces", defaults.deniedPdcNamespaces()),
                set(yaml, "persistent-data.ignored-namespaces", defaults.ignoredPdcNamespaces()),
                validator.bool("container-contents.enabled", yaml.get("container-contents.enabled"), defaults.containerContentsEnabled()),
                validator.positiveInt("container-contents.max-depth", yaml.getInt("container-contents.max-depth", defaults.containerMaxDepth()), defaults.containerMaxDepth()),
                validator.bool("container-contents.scan-inner-items", yaml.get("container-contents.scan-inner-items"), defaults.scanInnerItems()),
                validator.bool("custom-metadata.enabled", yaml.get("custom-metadata.enabled"), defaults.customMetadataEnabled()),
                validator.bool("custom-metadata.flag-custom-name", yaml.get("custom-metadata.flag-custom-name"), defaults.flagCustomName()),
                validator.bool("custom-metadata.flag-lore", yaml.get("custom-metadata.flag-lore"), defaults.flagLore()),
                validator.bool("custom-metadata.flag-custom-model-data", yaml.get("custom-metadata.flag-custom-model-data"), defaults.flagCustomModelData())
        );
    }

    private RiskSettings readRiskSettings(YamlConfiguration yaml, ConfigValidator validator) {
        RiskSettings defaults = RiskSettings.defaults();
        EnumMap<SignalType, Integer> scores = new EnumMap<>(SignalType.class);
        ConfigurationSection section = yaml.getConfigurationSection("scores");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    SignalType.valueOf(key);
                } catch (IllegalArgumentException ignored) {
                    validator.addWarning("scores." + key + " is not a known SignalType; ignored");
                }
            }
        }
        for (SignalType type : SignalType.values()) {
            int fallback = defaults.score(type);
            int configured = section == null ? fallback : section.getInt(type.name(), fallback);
            scores.put(type, validator.clampInt("scores." + type.name(), configured, 0, 100, fallback));
        }
        int suspicious = validator.clampInt("levels.suspicious", yaml.getInt("levels.suspicious", defaults.suspiciousThreshold()), 0, 100, defaults.suspiciousThreshold());
        int high = validator.clampInt("levels.high", yaml.getInt("levels.high", defaults.highThreshold()), 0, 100, defaults.highThreshold());
        int critical = validator.clampInt("levels.critical", yaml.getInt("levels.critical", defaults.criticalThreshold()), 0, 100, defaults.criticalThreshold());
        if (suspicious > high || high > critical) {
            validator.addWarning("levels must be ascending (suspicious <= high <= critical); using defaults");
            suspicious = defaults.suspiciousThreshold();
            high = defaults.highThreshold();
            critical = defaults.criticalThreshold();
        }
        return new RiskSettings(
                Map.copyOf(scores),
                suspicious,
                high,
                critical,
                Duration.ofMillis(validator.positiveLong("windows.rapid-gain-millis", yaml.getLong("windows.rapid-gain-millis", defaults.rapidGainWindow().toMillis()), defaults.rapidGainWindow().toMillis())),
                Duration.ofMillis(validator.positiveLong("windows.high-value-burst-millis", yaml.getLong("windows.high-value-burst-millis", defaults.highValueBurstWindow().toMillis()), defaults.highValueBurstWindow().toMillis())),
                Duration.ofMillis(validator.positiveLong("windows.repeated-identical-millis", yaml.getLong("windows.repeated-identical-millis", defaults.repeatedIdenticalWindow().toMillis()), defaults.repeatedIdenticalWindow().toMillis())),
                Duration.ofMillis(validator.positiveLong("windows.shulker-flow-millis", yaml.getLong("windows.shulker-flow-millis", defaults.shulkerFlowWindow().toMillis()), defaults.shulkerFlowWindow().toMillis())),
                Duration.ofMillis(validator.positiveLong("windows.signal-ttl-millis", yaml.getLong("windows.signal-ttl-millis", defaults.signalTtl().toMillis()), defaults.signalTtl().toMillis())),
                validator.positiveInt("thresholds.rapid-gain-amount", yaml.getInt("thresholds.rapid-gain-amount", defaults.rapidGainAmount()), defaults.rapidGainAmount()),
                validator.positiveInt("thresholds.high-value-burst-score", yaml.getInt("thresholds.high-value-burst-score", defaults.highValueBurstScore()), defaults.highValueBurstScore()),
                validator.positiveInt("thresholds.repeated-identical-amount", yaml.getInt("thresholds.repeated-identical-amount", defaults.repeatedIdenticalAmount()), defaults.repeatedIdenticalAmount()),
                validator.positiveInt("thresholds.shulker-high-flow-transfers", yaml.getInt("thresholds.shulker-high-flow-transfers", defaults.shulkerHighFlowTransfers()), defaults.shulkerHighFlowTransfers()),
                validator.positiveLong("thresholds.shulker-rapid-transfer-millis", yaml.getLong("thresholds.shulker-rapid-transfer-millis", defaults.shulkerRapidTransferMillis()), defaults.shulkerRapidTransferMillis()),
                validator.positiveInt("thresholds.unexplained-min-amount", yaml.getInt("thresholds.unexplained-min-amount", defaults.unexplainedMinAmount()), defaults.unexplainedMinAmount()),
                Duration.ofMillis(validator.positiveLong("cooldowns.same-signal-millis", yaml.getLong("cooldowns.same-signal-millis", defaults.sameSignalCooldown().toMillis()), defaults.sameSignalCooldown().toMillis())),
                Duration.ofMillis(validator.positiveLong("cooldowns.alert-millis", yaml.getLong("cooldowns.alert-millis", defaults.alertCooldown().toMillis()), defaults.alertCooldown().toMillis())),
                validator.bool("deduplicate-same-correlation", yaml.get("deduplicate-same-correlation"), defaults.deduplicateSameCorrelation()),
                validator.clampInt("family-caps.custom-metadata", yaml.getInt("family-caps.custom-metadata", defaults.customMetadataFamilyCap()), 0, 100, defaults.customMetadataFamilyCap())
        );
    }

    private AlertSettings readAlertSettings(YamlConfiguration yaml, ConfigValidator validator) {
        AlertSettings defaults = AlertSettings.defaults();
        return new AlertSettings(
                validator.bool("enabled", yaml.get("enabled"), defaults.enabled()),
                validator.clampInt("threshold", yaml.getInt("threshold", defaults.threshold()), 0, 100, defaults.threshold()),
                validator.clampInt("critical-threshold", yaml.getInt("critical-threshold", defaults.criticalThreshold()), 0, 100, defaults.criticalThreshold()),
                validator.bool("console", yaml.get("console"), defaults.console()),
                validator.bool("admin-chat", yaml.get("admin-chat"), defaults.adminChat()),
                yaml.getString("broadcast-to-permission", defaults.permission()),
                validator.bool("aggregation.enabled", yaml.get("aggregation.enabled"), defaults.aggregationEnabled()),
                Duration.ofMillis(validator.positiveLong("aggregation.window-millis", yaml.getLong("aggregation.window-millis", defaults.aggregationWindow().toMillis()), defaults.aggregationWindow().toMillis())),
                validator.bool("buttons.inspect", yaml.get("buttons.inspect"), defaults.inspectButton()),
                validator.bool("buttons.trace", yaml.get("buttons.trace"), defaults.traceButton()),
                validator.bool("buttons.teleport", yaml.get("buttons.teleport"), defaults.teleportButton()),
                yaml.getString("trace-duration", defaults.traceDuration())
        );
    }

    private IntegrationSettings readIntegrationSettings(YamlConfiguration yaml, ConfigValidator validator) {
        IntegrationSettings defaults = IntegrationSettings.defaults();
        return new IntegrationSettings(
                validator.bool("husksync.enabled", yaml.get("husksync.enabled"), defaults.huskSyncEnabled()),
                validator.bool("husksync.attribute-sync-as-expected", yaml.get("husksync.attribute-sync-as-expected"), defaults.huskSyncAttributeAsExpected()),
                Duration.ofMillis(validator.positiveLong("husksync.timeout-millis", yaml.getLong("husksync.timeout-millis", defaults.huskSyncTimeout().toMillis()), defaults.huskSyncTimeout().toMillis()))
        );
    }

    private LoggingSettings readLoggingSettings(YamlConfiguration yaml, ConfigValidator validator, Path dataFolder) {
        LoggingSettings defaults = LoggingSettings.defaults(dataFolder);
        ConfigurationSection section = yaml.getConfigurationSection("logging");
        if (section == null) {
            section = yaml;
        }
        boolean enabled = validator.bool("logging.enabled", section.get("enabled"), defaults.enabled());
        String format = section.getString("format", defaults.format());
        if (format == null || !format.equalsIgnoreCase("JSONL")) {
            validator.addWarning("logging.format = " + format + " is unsupported; using JSONL");
            format = "JSONL";
        }
        String directory = section.getString("directory", defaults.directory());
        boolean fallback = !dev.itemguard.log.LogDirectories.safeRelative(directory);
        if (fallback) {
            validator.addWarning("logging.directory = " + directory + " is outside the plugin folder; using logs");
            directory = "logs";
        }
        Path logsRoot = dev.itemguard.log.LogDirectories.resolve(dataFolder, directory);
        int retention = validator.clampInt("logging.retention-days", section.getInt("retention-days", defaults.retentionDays()), 1, 3650, defaults.retentionDays());
        int maxMb = validator.clampInt("logging.max-file-size-mb", section.getInt("max-file-size-mb", 100), 1, 1024, 100);
        int flushSeconds = validator.clampInt("logging.flush-interval-seconds", section.getInt("flush-interval-seconds", 2), 1, 60, 2);
        int capacity = validator.clampInt("logging.queue.capacity", section.getInt("queue.capacity", defaults.queueCapacity()), 32, 1_000_000, defaults.queueCapacity());
        ConfigurationSection categories = section.getConfigurationSection("categories");
        boolean alerts = loggingBool(categories, validator, "logging.categories.alerts", "alerts", defaults.logAlerts());
        boolean unknown = loggingBool(categories, validator, "logging.categories.unknown-gains", "unknown-gains", defaults.logUnknownGains());
        boolean invalid = loggingBool(categories, validator, "logging.categories.invalid-items", "invalid-items", defaults.logInvalidItems());
        boolean suspicious = loggingBool(categories, validator, "logging.categories.suspicious-items", "suspicious-items", defaults.logSuspiciousItems());
        boolean husk = loggingBool(categories, validator, "logging.categories.husksync-data-apply", "husksync-data-apply", defaults.logHuskSync());
        boolean admin = loggingBool(categories, validator, "logging.categories.admin-actions", "admin-actions", defaults.logAdminActions());
        boolean highValue = loggingBool(categories, validator, "logging.categories.high-value-flows", "high-value-flows", defaults.logHighValueFlows());
        int minValue = validator.clampInt(
                "logging.high-value-flow.minimum-item-value",
                section.getInt("high-value-flow.minimum-item-value", defaults.highValueMinimum()),
                0,
                100,
                defaults.highValueMinimum()
        );
        int warnSeconds = validator.clampInt(
                "logging.console-warning.queue-drop-warning-interval-seconds",
                section.getInt("console-warning.queue-drop-warning-interval-seconds", 60),
                5,
                3600,
                60
        );
        String serverName = section.getString("server-name", defaults.serverName());
        if (serverName == null || serverName.isBlank()) {
            serverName = "unknown";
        }
        return new LoggingSettings(
                enabled,
                "JSONL",
                directory,
                logsRoot,
                fallback,
                retention,
                maxMb * 1024L * 1024L,
                Duration.ofSeconds(flushSeconds),
                capacity,
                alerts,
                unknown,
                invalid,
                suspicious,
                husk,
                admin,
                highValue,
                minValue,
                Duration.ofSeconds(warnSeconds),
                defaults.shutdownTimeout(),
                defaults.reopenInterval(),
                serverName.trim()
        );
    }

    private static boolean loggingBool(ConfigurationSection categories, ConfigValidator validator, String path, String key, boolean fallback) {
        if (categories == null) {
            return fallback;
        }
        return validator.bool(path, categories.get(key), fallback);
    }

    private ItemValueRegistry readItemValues(YamlConfiguration yaml, ConfigValidator validator) {
        int defaultValue = validator.clampInt("default-value", yaml.getInt("default-value", 1), 0, 100, 1);
        Map<String, Integer> values = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("values");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key, validator.clampInt("values." + key, section.getInt(key), 0, 100, defaultValue));
            }
        }
        return new ItemValueRegistry(defaultValue, values);
    }

    private static Set<String> set(YamlConfiguration yaml, String path, Set<String> fallback) {
        List<String> list = yaml.getStringList(path);
        if (list.isEmpty() && yaml.get(path) == null) {
            return fallback;
        }
        Set<String> values = new LinkedHashSet<>();
        for (String value : list) {
            values.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private static Duration duration(YamlConfiguration yaml, String minutesPath, Duration fallback, boolean minutes, ConfigValidator validator) {
        if (!minutes) {
            return fallback;
        }
        int value = validator.positiveInt(minutesPath, yaml.getInt(minutesPath, (int) fallback.toMinutes()), (int) Math.max(1, fallback.toMinutes()));
        return Duration.ofMinutes(value);
    }

    private static Duration parseDuration(String raw, Duration fallback, String path, ConfigValidator validator) {
        return DurationParser.parse(raw).orElseGet(() -> {
            validator.addWarning(path + " = " + raw + " (invalid duration); using " + fallback);
            return fallback;
        });
    }
}
