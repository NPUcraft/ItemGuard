package com.npucraft.itemguard.command;

import com.npucraft.itemguard.alert.AlertRecord;
import com.npucraft.itemguard.config.ConfigManager;
import com.npucraft.itemguard.flow.ExpectedFlowCredit;
import com.npucraft.itemguard.flow.ExpectedFlowService;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.integration.IntegrationManager;
import com.npucraft.itemguard.risk.RiskLevel;
import com.npucraft.itemguard.risk.incident.RiskIncidentSnapshot;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.scan.IllegalItemScanner;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanFinding;
import com.npucraft.itemguard.session.PlayerGuardSession;
import com.npucraft.itemguard.session.SessionManager;
import com.npucraft.itemguard.trace.TraceService;
import com.npucraft.itemguard.util.Texts;
import com.npucraft.itemguard.log.ForensicLogPublisher;
import com.npucraft.itemguard.log.ForensicLogSink;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ItemGuardCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SessionManager sessions;
    private final TraceService traces;
    private final ExpectedFlowService expectedFlows;
    private final ReconciliationService reconciliation;
    private final IllegalItemScanner scanner;
    private final IntegrationManager integrations;
    private final Supplier<List<AlertRecord>> recentAlerts;
    private final Runnable reload;
    private final ForensicLogPublisher forensicPublisher;

    public ItemGuardCommand(
            JavaPlugin plugin,
            ConfigManager config,
            SessionManager sessions,
            TraceService traces,
            ExpectedFlowService expectedFlows,
            ReconciliationService reconciliation,
            IllegalItemScanner scanner,
            IntegrationManager integrations,
            Supplier<List<AlertRecord>> recentAlerts,
            Runnable reload,
            ForensicLogPublisher forensicPublisher
    ) {
        this.plugin = plugin;
        this.config = config;
        this.sessions = sessions;
        this.traces = traces;
        this.expectedFlows = expectedFlows;
        this.reconciliation = reconciliation;
        this.scanner = scanner;
        this.integrations = integrations;
        this.recentAlerts = recentAlerts;
        this.reload = reload;
        this.forensicPublisher = forensicPublisher;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            return help(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "inspect" -> inspect(sender, args);
            case "trace" -> trace(sender, args);
            case "scan" -> scan(sender, args);
            case "alerts" -> alerts(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            default -> {
                send(sender, "unknown-subcommand", "<red>Unknown subcommand. Use <white>/ig help</white>.");
                yield true;
            }
        };
    }

    private boolean help(CommandSender sender) {
        if (!hasAny(sender)) {
            return deny(sender);
        }
        sendRaw(sender, config.message("help-header", "<gold>ItemGuard commands"));
        helpLine(sender, "status", "Plugin status", "itemguard.status");
        helpLine(sender, "inspect <player>", "Inspect a player session", "itemguard.inspect");
        helpLine(sender, "trace <player> [duration] [material]", "Item flow timeline", "itemguard.trace");
        helpLine(sender, "scan <player>", "Scan inventory", "itemguard.scan");
        helpLine(sender, "alerts", "Recent alerts", "itemguard.alerts");
        helpLine(sender, "reload", "Reload configuration", "itemguard.reload");
        helpLine(sender, "debug <player>", "Debug a session", "itemguard.debug");
        helpLine(sender, "help", "This list", "itemguard.status");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("itemguard.status") && !sender.hasPermission("itemguard.admin")) {
            return deny(sender);
        }
        sendRaw(sender, config.message("status-header", "<gold>ItemGuard status"));
        sender.sendMessage(plain("Version: " + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(plain("Paper: " + Bukkit.getMinecraftVersion() + " (" + Bukkit.getVersion() + ")"));
        sender.sendMessage(plain("Active sessions: " + sessions.size()));
        int activeIncidents = 0;
        int highIncidents = 0;
        int criticalIncidents = 0;
        for (PlayerGuardSession session : sessions.all()) {
            for (RiskIncidentSnapshot incident : session.activeIncidents()) {
                activeIncidents++;
                if (incident.level() == RiskLevel.CRITICAL) {
                    criticalIncidents++;
                } else if (incident.level() == RiskLevel.HIGH) {
                    highIncidents++;
                }
            }
        }
        sender.sendMessage(plain("Active Incidents: " + activeIncidents));
        sender.sendMessage(plain("High: " + highIncidents));
        sender.sendMessage(plain("Critical: " + criticalIncidents));
        sender.sendMessage(plain("Trace events: " + traces.totalEvents()));
        sender.sendMessage(plain("HuskSync detected: " + integrations.huskSyncDetected()));
        sender.sendMessage(plain("HuskSync integration active: " + integrations.huskSyncActive()));
        sender.sendMessage(plain("Reconciliation interval: " + config.pluginSettings().reconcileIntervalTicks() + " ticks"));
        sender.sendMessage(plain("Scanner: " + (config.pluginSettings().scannerEnabled() ? "enabled" : "disabled")));
        sender.sendMessage(plain("Creative inventory gains: " + (config.pluginSettings().ignoreCreativeInventoryGains() ? "ignored (CREATIVE_INVENTORY)" : "treated as UNKNOWN if unexplained")));
        sender.sendMessage(plain("Risk engine: enabled"));
        sender.sendMessage(plain("Item flow monitor: enabled"));
        sender.sendMessage(plain("Last reconcile: " + reconciliation.performance().lastReconcileMillis() + "ms"));
        sender.sendMessage(plain("Average reconcile: " + reconciliation.performance().averageReconcileMillis() + "ms ("
                + reconciliation.performance().reconcileCount() + " runs)"));
        sender.sendMessage(plain("Last scanner: " + reconciliation.performance().lastScanMillis() + "ms"));
        sender.sendMessage(plain("Dirty queue: " + reconciliation.performance().lastDirtyQueue()));
        sender.sendMessage(plain("Expected credits: " + expectedFlows.ledger().totalSize()));
        ForensicLogSink forensic = forensicPublisher.sink();
        sender.sendMessage(plain("Forensic Logging: " + (forensic.enabled() ? "enabled" : "disabled")));
        if (forensic.enabled()) {
            sender.sendMessage(plain(forensic.metrics().statusLine()));
        }
        return true;
    }

    private boolean inspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("itemguard.inspect") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "INSPECT", null, args.length >= 2 ? args[1] : null, "DENIED");
            return deny(sender);
        }
        if (args.length < 2) {
            forensicPublisher.onAdminAction(sender, "INSPECT", null, null, "FAILED");
            sender.sendMessage(plain("Usage: /ig inspect <player>"));
            return true;
        }
        UUID playerId = sessions.lookup(args[1]);
        if (playerId == null) {
            forensicPublisher.onAdminAction(sender, "INSPECT", null, args[1], "FAILED");
            send(sender, "unknown-player", "<red>Unknown player: <white><player>", Map.of("player", args[1]));
            return true;
        }
        forensicPublisher.onAdminAction(sender, "INSPECT", playerId, sessions.nameOf(playerId), "SUCCESS");
        PlayerGuardSession session = sessions.get(playerId);
        Player online = Bukkit.getPlayer(playerId);
        sendRaw(sender, config.message("inspect-header", "<gold>ItemGuard inspect</gold> <gray><player>"), Map.of("player", sessions.nameOf(playerId)));
        sender.sendMessage(plain("UUID: " + playerId));
        if (session == null) {
            send(sender, "player-offline-session", "<yellow>Player is offline. Showing retained memory data where available.");
        } else {
            sender.sendMessage(plain("Current Risk: " + session.currentRiskScore() + "/100 " + session.currentRiskLevel()
                    + " (highest active incident)"));
            List<RiskIncidentSnapshot> incidents = session.activeIncidents();
            if (incidents.isEmpty()) {
                sender.sendMessage(plain("Highest Incident: none"));
                sender.sendMessage(plain("Active Incidents: 0"));
            } else {
                RiskIncidentSnapshot highest = incidents.stream()
                        .max(java.util.Comparator.comparingInt(RiskIncidentSnapshot::score))
                        .orElse(incidents.getFirst());
                sender.sendMessage(plain("Highest Incident: #" + highest.shortId() + " " + highest.type() + " " + highest.material()));
                sender.sendMessage(plain("Active Incidents: " + incidents.size()));
                for (RiskIncidentSnapshot incident : incidents) {
                    sender.sendMessage(plain("  #" + incident.shortId()
                            + " " + incident.score()
                            + " " + incident.type()
                            + " " + incident.material()
                            + " " + incident.summary()));
                }
            }
            sender.sendMessage(plain("Unknown gains (session): " + session.unknownGainCount()));
            sender.sendMessage(plain("Suspicious items: " + session.suspiciousItemCount()));
            sender.sendMessage(plain("HuskSync: " + session.huskSync().state()));
            sender.sendMessage(plain("Last reconcile: " + String.valueOf(session.lastReconcileTime())));
            if (session.lastContainer() != null) {
                sender.sendMessage(plain("Last container: " + session.lastContainer().format()));
            }
            sender.sendMessage(plain("Shulker transfers: " + session.shulker().transfers()
                    + " lastOpen=" + session.shulker().lastOpen()
                    + " lastClose=" + session.shulker().lastClose()));
            sender.sendMessage(plain("Recent gains:"));
            session.gainWindow().recent(8).forEach(gain ->
                    sender.sendMessage(plain("  +" + gain.amount() + " " + gain.material() + " via " + gain.source() + (gain.unexplained() ? " ⚠" : ""))));
            sender.sendMessage(plain("Recent signals:"));
            session.signals().stream().limit(8).forEach(signal ->
                    sender.sendMessage(plain("  " + signal.type() + " +" + signal.scoreContribution() + " " + signal.description())));
        }
        if (online == null && session == null) {
            sender.sendMessage(plain("No live session. Trace history may still be available via /ig trace."));
        }
        return true;
    }

    private boolean trace(CommandSender sender, String[] args) {
        if (!sender.hasPermission("itemguard.trace") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "TRACE", null, args.length >= 2 ? args[1] : null, "DENIED");
            return deny(sender);
        }
        if (args.length < 2) {
            forensicPublisher.onAdminAction(sender, "TRACE", null, null, "FAILED");
            sender.sendMessage(plain("Usage: /ig trace <player> [duration] [material]"));
            return true;
        }
        UUID playerId = sessions.lookup(args[1]);
        if (playerId == null) {
            forensicPublisher.onAdminAction(sender, "TRACE", null, args[1], "FAILED");
            send(sender, "unknown-player", "<red>Unknown player: <white><player>", Map.of("player", args[1]));
            return true;
        }
        Duration duration = Duration.ofMinutes(5);
        if (args.length >= 3) {
            var parsed = DurationParser.parse(args[2]);
            if (parsed.isEmpty()) {
                forensicPublisher.onAdminAction(sender, "TRACE", playerId, sessions.nameOf(playerId), "FAILED");
                send(sender, "invalid-duration", "<red>Invalid duration '<white><input></white>'.", Map.of("input", args[2]));
                return true;
            }
            duration = parsed.get();
            if (duration.compareTo(config.pluginSettings().maxTraceQuery()) > 0) {
                forensicPublisher.onAdminAction(sender, "TRACE", playerId, sessions.nameOf(playerId), "FAILED");
                send(sender, "duration-too-large", "<red>Duration exceeds maximum of <white><max>", Map.of("max", DurationParser.format(config.pluginSettings().maxTraceQuery())));
                return true;
            }
        }
        String material = null;
        if (args.length >= 4) {
            Material matched = Material.matchMaterial(args[3]);
            if (matched == null || matched.isAir() || !matched.isItem()) {
                forensicPublisher.onAdminAction(sender, "TRACE", playerId, sessions.nameOf(playerId), "FAILED");
                send(sender, "invalid-material", "<red>Unknown material: <white><material>", Map.of("material", args[3]));
                return true;
            }
            material = matched.name();
        }
        forensicPublisher.onAdminAction(sender, "TRACE", playerId, sessions.nameOf(playerId), "SUCCESS");
        List<ItemFlowEvent> events = traces.query(playerId, duration, material);
        sendRaw(sender, config.message("trace-header", "<gold>ItemGuard trace</gold> <gray><player> — last <duration>"),
                Map.of("player", sessions.nameOf(playerId), "duration", DurationParser.format(duration)));
        if (events.isEmpty()) {
            sender.sendMessage(plain("No flow events in range."));
            return true;
        }
        for (ItemFlowEvent event : events) {
            String sign = event.amountDelta() >= 0 ? "+" : "";
            sender.sendMessage(plain(sign + event.amountDelta() + " " + event.material()));
            sender.sendMessage(sourceLine(event));
        }
        return true;
    }

    private Component sourceLine(ItemFlowEvent event) {
        if (event.source() == com.npucraft.itemguard.flow.model.FlowSource.HUSKSYNC_DATA_APPLY) {
            return Texts.parse(config.message("flow-source-husksync", "<gray>↳ <aqua>HuskSync Data Apply</aqua> <dark_gray>(trusted external source)"),
                    Map.of("destination", event.destination().name()));
        }
        if (event.source() == com.npucraft.itemguard.flow.model.FlowSource.CREATIVE_INVENTORY) {
            return Texts.parse(config.message("flow-source-creative", "<gray>↳ <aqua>CREATIVE_INVENTORY</aqua> → <white><destination>"),
                    Map.of("destination", event.destination().name()));
        }
        if (event.isUnexplained() || event.confidence() == SourceConfidence.UNKNOWN) {
            return Texts.parse(config.message("flow-source-unknown", "<gray>↳ <red>UNKNOWN SOURCE ⚠"),
                    Map.of("confidence", event.confidence().name()));
        }
        if (event.confidence() == SourceConfidence.INFERRED) {
            if (event.container() != null) {
                return Texts.parse(config.message("flow-source-inferred-container", "<gray>↳ <yellow><source></yellow> @ <white><location></white> → <white><destination></white> <dark_gray>(inferred)"),
                        Map.of(
                                "source", event.source().name(),
                                "location", event.container().format(),
                                "destination", event.destination().name()
                        ));
            }
            return Texts.parse(config.message("flow-source-inferred", "<gray>↳ <yellow><source></yellow> → <white><destination></white> <dark_gray>(inferred)"),
                    Map.of(
                            "source", event.source().name(),
                            "destination", event.destination().name(),
                            "confidence", event.confidence().name()
                    ));
        }
        if (event.container() != null) {
            return Texts.parse(config.message("flow-source-container", "<gray>↳ <yellow><source></yellow> @ <white><location>"),
                    Map.of(
                            "source", event.source().name(),
                            "location", event.container().format(),
                            "destination", event.destination().name()
                    ));
        }
        if (event.source() == com.npucraft.itemguard.flow.model.FlowSource.SHULKER_BOX) {
            return Texts.parse(config.message("flow-source-shulker", "<gray>↳ <light_purple>Shulker Box"),
                    Map.of("destination", event.destination().name()));
        }
        return Texts.parse(config.message("flow-source-verified", "<gray>↳ <green><source></green> → <white><destination>"),
                Map.of(
                        "source", event.source().name(),
                        "destination", event.destination().name(),
                        "confidence", event.confidence().name()
                ));
    }

    private boolean scan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("itemguard.scan") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "SCAN", null, args.length >= 2 ? args[1] : null, "DENIED");
            return deny(sender);
        }
        if (args.length < 2) {
            forensicPublisher.onAdminAction(sender, "SCAN", null, null, "FAILED");
            sender.sendMessage(plain("Usage: /ig scan <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            forensicPublisher.onAdminAction(sender, "SCAN", null, args[1], "FAILED");
            send(sender, "unknown-player", "<red>Unknown player: <white><player>", Map.of("player", args[1]));
            return true;
        }
        forensicPublisher.onAdminAction(sender, "SCAN", target.getUniqueId(), target.getName(), "SUCCESS");
        sendRaw(sender, config.message("scan-header", "<gold>ItemGuard scan</gold> <gray><player>"), Map.of("player", target.getName()));
        List<ScanFinding> findings = scanner.scanInventory(target, config.pluginSettings().maxScanDepth());
        reconciliation.reconcile(target, "admin-scan");
        PlayerGuardSession session = sessions.get(target.getUniqueId());
        if (session != null) {
            session.setSuspiciousItemCount((int) findings.stream()
                    .filter(finding -> finding.classification() == ScanClassification.INVALID || finding.classification() == ScanClassification.SUSPICIOUS)
                    .count());
        }
        if (findings.isEmpty()) {
            send(sender, "scan-clean", "<green>No scanner findings on current inventory.");
            return true;
        }
        findings.forEach(finding -> sender.sendMessage(plain(
                finding.classification() + " " + finding.signalType() + " " + finding.material() + " — " + finding.description()
        )));
        return true;
    }

    private boolean alerts(CommandSender sender) {
        if (!sender.hasPermission("itemguard.alerts") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "ALERTS", null, null, "DENIED");
            return deny(sender);
        }
        forensicPublisher.onAdminAction(sender, "ALERTS", null, null, "SUCCESS");
        sendRaw(sender, config.message("alerts-header", "<gold>Recent ItemGuard alerts"));
        List<AlertRecord> records = recentAlerts.get();
        if (records.isEmpty()) {
            send(sender, "alerts-empty", "<gray>No recent alerts in memory.");
            return true;
        }
        records.stream().skip(Math.max(0, records.size() - 15)).forEach(record ->
                sender.sendMessage(plain(record.playerName() + " risk " + record.riskScore()
                        + " +" + record.amount() + " " + record.material()
                        + " (" + record.aggregatedEvents() + " events)")));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("itemguard.reload") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "RELOAD", null, null, "DENIED");
            return deny(sender);
        }
        reload.run();
        forensicPublisher.onAdminAction(sender, "RELOAD", null, null, "SUCCESS");
        if (config.lastReloadHadWarnings()) {
            send(sender, "reload-partial", "<yellow>Configuration reloaded with warnings. Check the console.");
        } else {
            send(sender, "reload-success", "<green>Configuration reloaded.");
        }
        return true;
    }

    private boolean debug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("itemguard.debug") && !sender.hasPermission("itemguard.admin")) {
            forensicPublisher.onAdminAction(sender, "DEBUG", null, args.length >= 2 ? args[1] : null, "DENIED");
            return deny(sender);
        }
        if (args.length < 2) {
            forensicPublisher.onAdminAction(sender, "DEBUG", null, null, "FAILED");
            sender.sendMessage(plain("Usage: /ig debug <player>"));
            return true;
        }
        UUID playerId = sessions.lookup(args[1]);
        if (playerId == null) {
            forensicPublisher.onAdminAction(sender, "DEBUG", null, args[1], "FAILED");
            send(sender, "unknown-player", "<red>Unknown player: <white><player>", Map.of("player", args[1]));
            return true;
        }
        forensicPublisher.onAdminAction(sender, "DEBUG", playerId, sessions.nameOf(playerId), "SUCCESS");
        Instant now = Instant.now();
        PlayerGuardSession session = sessions.get(playerId);
        String name = sessions.nameOf(playerId);
        sendRaw(sender, config.message("debug-header", "<gold>ItemGuard debug</gold> <gray><player>"), Map.of("player", name));
        send(sender, "debug-disclaimer", "<gray>Diagnostic information only. Item NBT, PDC, and secrets are not shown.");
        sender.sendMessage(plain("UUID: " + playerId));
        sender.sendMessage(plain("Online: " + (Bukkit.getPlayer(playerId) != null)));
        if (session == null) {
            send(sender, "player-offline-session", "<yellow>Player is offline. Showing retained memory data where available.");
            sender.sendMessage(plain("Recent FlowEvents:"));
            List<ItemFlowEvent> retained = traces.recent(playerId, 12);
            if (retained.isEmpty()) {
                sender.sendMessage(plain("  (none)"));
            }
            retained.forEach(event -> sender.sendMessage(plain("  " + formatFlow(event))));
            return true;
        }

        InventorySnapshot snapshot = session.lastSnapshot();
        sender.sendMessage(plain("Baseline age: " + (snapshot == null ? "none" : formatAge(snapshot.timestamp(), now))));
        sender.sendMessage(plain("Last reconcile: " + (session.lastReconcileTime() == null ? "none" : session.lastReconcileTime()
                + " (" + formatAge(session.lastReconcileTime(), now) + " ago)")));
        sender.sendMessage(plain("Last reconcile duration: " + nanosToMillis(session.lastReconcileNanos()) + "ms"));
        sender.sendMessage(plain("Dirty: " + session.dirty() + " scheduled=" + session.reconcileScheduled()));
        sender.sendMessage(plain("Risk: " + session.currentRiskScore() + "/100 " + session.currentRiskLevel()
                + " (MAX of active incidents)"));
        List<RiskIncidentSnapshot> incidents = session.activeIncidents();
        sender.sendMessage(plain("Active incidents: " + incidents.size()));
        for (RiskIncidentSnapshot incident : incidents) {
            sender.sendMessage(plain("  #" + incident.shortId()
                    + " " + incident.score() + "/100 " + incident.level()
                    + " " + incident.type()
                    + " " + incident.material()
                    + " expires=" + formatAge(now, incident.expiresAt())
                    + " alerted=" + incident.highestAlertedLevel()));
        }
        if (session.lastAttribution() != null) {
            var decision = session.lastAttribution();
            sender.sendMessage(plain("Last attribution: " + decision.reason()
                    + " " + decision.material()
                    + " +" + decision.actualAmount()
                    + " selected=" + decision.selectedSource()
                    + " kind=" + decision.selectedKind()
                    + " hints=" + decision.pendingHintsCount()
                    + " exact=" + decision.expectedCreditsCount()
                    + " nearestAgeMs=" + decision.nearestHintAgeMs()));
        }
        sender.sendMessage(plain("HuskSync state: " + session.huskSync().state()));
        sender.sendMessage(plain("HuskSync transaction id: " + session.huskSync().transactionId()
                + " open=" + session.huskSync().openCount()
                + " note=" + session.huskSync().note()));

        sender.sendMessage(plain("Material totals (top-level): " + (snapshot == null ? "{}" : snapshot.materialTotals())));
        sender.sendMessage(plain("Nested totals: " + (snapshot == null ? "{}" : snapshot.nestedTotals())));
        sender.sendMessage(plain("Recent inventory diff: " + session.lastDiff()));

        sender.sendMessage(plain("Expected credits:"));
        List<ExpectedFlowCredit> credits = expectedFlows.ledger().snapshot(playerId, now);
        if (credits.isEmpty()) {
            sender.sendMessage(plain("  (none)"));
        }
        for (ExpectedFlowCredit credit : credits) {
            sender.sendMessage(plain("  " + formatCredit(credit, now)));
        }

        sender.sendMessage(plain("Recent FlowEvents:"));
        List<ItemFlowEvent> flows = traces.recent(playerId, 12);
        if (flows.isEmpty()) {
            sender.sendMessage(plain("  (none)"));
        }
        flows.forEach(event -> sender.sendMessage(plain("  " + formatFlow(event))));

        sender.sendMessage(plain("Recent RiskSignals:"));
        List<RiskSignal> signals = session.signals();
        if (signals.isEmpty()) {
            sender.sendMessage(plain("  (none)"));
        }
        for (RiskSignal signal : signals) {
            String expiry = signal.expiresAt() == null ? "n/a" : formatAge(now, signal.expiresAt()) + " remaining";
            sender.sendMessage(plain("  " + signal.type() + " +" + signal.scoreContribution()
                    + " expired=" + signal.isExpired(now)
                    + " " + expiry
                    + " " + signal.description()));
        }
        return true;
    }

    private static String formatCredit(ExpectedFlowCredit credit, Instant now) {
        String hint = credit.oneShot() ? "SOURCE_HINT" : "EXACT";
        long remainingMs = Math.max(0, Duration.between(now, credit.expiresAt()).toMillis());
        return credit.material()
                + " source=" + credit.source()
                + " confidence=" + credit.confidence()
                + " created=" + credit.createdAt()
                + " expiresIn=" + remainingMs + "ms"
                + " oneShot=" + credit.oneShot()
                + " remaining=" + credit.remainingAmount()
                + " status=" + hint
                + " corr=" + credit.correlationId();
    }

    private static String formatFlow(ItemFlowEvent event) {
        String sign = event.amountDelta() >= 0 ? "+" : "";
        return sign + event.amountDelta() + " " + event.material()
                + " " + event.source() + " -> " + event.destination()
                + " " + event.confidence()
                + (event.container() == null ? "" : " @" + event.container().format());
    }

    private static String formatAge(Instant then, Instant now) {
        if (then == null) {
            return "n/a";
        }
        Duration duration = Duration.between(then, now);
        if (duration.isNegative()) {
            duration = duration.negated();
        }
        long seconds = duration.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private static String nanosToMillis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private void helpLine(CommandSender sender, String command, String description, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("itemguard.admin")) {
            sendRaw(sender, config.message("help-line", "<yellow>/ig <command></yellow> <dark_gray>-</dark_gray> <gray><description>"),
                    Map.of("command", command, "description", description));
        }
    }

    private boolean hasAny(CommandSender sender) {
        return sender.hasPermission("itemguard.admin")
                || sender.hasPermission("itemguard.status")
                || sender.hasPermission("itemguard.inspect")
                || sender.hasPermission("itemguard.trace")
                || sender.hasPermission("itemguard.scan")
                || sender.hasPermission("itemguard.alerts")
                || sender.hasPermission("itemguard.reload")
                || sender.hasPermission("itemguard.debug");
    }

    private boolean deny(CommandSender sender) {
        send(sender, "no-permission", "<red>You do not have permission to use this command.");
        return true;
    }

    private void send(CommandSender sender, String key, String fallback) {
        sendRaw(sender, config.message("prefix", "<dark_gray>[<gold>ItemGuard</gold>]</dark_gray> ") + config.message(key, fallback));
    }

    private void send(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        sendRaw(sender, config.message("prefix", "<dark_gray>[<gold>ItemGuard</gold>]</dark_gray> ") + config.message(key, fallback), placeholders);
    }

    private void sendRaw(CommandSender sender, String mini) {
        audience(sender).sendMessage(Texts.parse(mini));
    }

    private void sendRaw(CommandSender sender, String mini, Map<String, String> placeholders) {
        audience(sender).sendMessage(Texts.parse(mini, placeholders));
    }

    private static Audience audience(CommandSender sender) {
        return sender;
    }

    private static Component plain(String text) {
        return Component.text(text);
    }

    private boolean allowed(CommandSender sender, String permission) {
        return sender.hasPermission("itemguard.admin") || sender.hasPermission(permission);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!hasAny(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            addSubcommand(sender, suggestions, "help", null);
            addIfAllowed(sender, suggestions, "status", "itemguard.status");
            addIfAllowed(sender, suggestions, "inspect", "itemguard.inspect");
            addIfAllowed(sender, suggestions, "trace", "itemguard.trace");
            addIfAllowed(sender, suggestions, "scan", "itemguard.scan");
            addIfAllowed(sender, suggestions, "alerts", "itemguard.alerts");
            addIfAllowed(sender, suggestions, "reload", "itemguard.reload");
            addIfAllowed(sender, suggestions, "debug", "itemguard.debug");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return suggestions.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("inspect", "trace", "scan", "debug").contains(sub)) {
            if (!allowed(sender, "itemguard." + sub)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        if (!"trace".equals(sub) || !allowed(sender, "itemguard.trace")) {
            return List.of();
        }
        if (args.length == 3) {
            return Stream.of("30s", "5m", "10m", "30m", "1h")
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 4) {
            String prefix = args[3].toUpperCase(Locale.ROOT);
            List<String> materials = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isItem() || material.isAir()) {
                    continue;
                }
                String name = material.name();
                if (name.startsWith(prefix)) {
                    materials.add(name);
                    if (materials.size() >= 32) {
                        break;
                    }
                }
            }
            return materials;
        }
        return List.of();
    }

    private void addSubcommand(CommandSender sender, List<String> suggestions, String name, String permission) {
        if (permission == null || allowed(sender, permission)) {
            suggestions.add(name);
        }
    }

    private void addIfAllowed(CommandSender sender, List<String> suggestions, String name, String permission) {
        addSubcommand(sender, suggestions, name, permission);
    }
}
