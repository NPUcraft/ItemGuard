package dev.itemguard.api;

import dev.itemguard.alert.AlertRecord;
import dev.itemguard.alert.AlertService;
import dev.itemguard.config.ConfigManager;
import dev.itemguard.flow.ExpectedFlowCredit;
import dev.itemguard.flow.ExpectedFlowService;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.integration.IntegrationManager;
import dev.itemguard.scan.IllegalItemScanner;
import dev.itemguard.scan.ScanFinding;
import dev.itemguard.session.HuskSyncTransaction;
import dev.itemguard.session.PlayerGuardSession;
import dev.itemguard.session.SessionManager;
import dev.itemguard.trace.TraceService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ItemGuardDiagnosticsImpl implements ItemGuardDiagnostics {

    private final SessionManager sessions;
    private final TraceService traces;
    private final ExpectedFlowService expectedFlows;
    private final IllegalItemScanner scanner;
    private final AlertService alerts;
    private final ConfigManager config;
    private final IntegrationManager integrations;

    public ItemGuardDiagnosticsImpl(
            SessionManager sessions,
            TraceService traces,
            ExpectedFlowService expectedFlows,
            IllegalItemScanner scanner,
            AlertService alerts,
            ConfigManager config,
            IntegrationManager integrations
    ) {
        this.sessions = sessions;
        this.traces = traces;
        this.expectedFlows = expectedFlows;
        this.scanner = scanner;
        this.alerts = alerts;
        this.config = config;
        this.integrations = integrations;
    }

    @Override
    public PlayerDiagnosticSnapshot player(UUID playerId) {
        if (playerId == null) {
            return empty(null, "unknown", false);
        }
        return snapshot(playerId, sessions.nameOf(playerId));
    }

    @Override
    public PlayerDiagnosticSnapshot player(String name) {
        UUID playerId = sessions.lookup(name);
        if (playerId == null) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                playerId = online.getUniqueId();
            }
        }
        if (playerId == null) {
            return empty(null, name == null ? "unknown" : name, false);
        }
        return snapshot(playerId, sessions.nameOf(playerId));
    }

    @Override
    public List<ScanFindingView> scan(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        return scanner.scanInventory(player, config.pluginSettings().maxScanDepth()).stream()
                .map(ItemGuardDiagnosticsImpl::scanView)
                .toList();
    }

    @Override
    public boolean huskSyncDetected() {
        return detected();
    }

    @Override
    public boolean huskSyncActive() {
        return active();
    }

    @Override
    public Map<String, Object> runtimeSettings() {
        Map<String, Object> map = new LinkedHashMap<>();
        var pluginSettings = config.pluginSettings();
        var alerts = config.alertSettings();
        var risk = config.riskSettings();
        var scanner = config.scannerSettings();
        var integrations = config.integrationSettings();
        var logging = config.loggingSettings();
        map.put("debug", pluginSettings.debug());
        map.put("traceRetentionMinutes", pluginSettings.traceRetention().toMinutes());
        map.put("reconcileIntervalTicks", pluginSettings.reconcileIntervalTicks());
        map.put("heartbeatIntervalTicks", pluginSettings.heartbeatIntervalTicks());
        map.put("alertThreshold", alerts.threshold());
        map.put("criticalThreshold", alerts.criticalThreshold());
        map.put("alertAggregationWindowMillis", alerts.aggregationWindow().toMillis());
        map.put("riskHighThreshold", risk.highThreshold());
        map.put("riskCriticalThreshold", risk.criticalThreshold());
        map.put("diamondValue", config.itemValues().valueOf("DIAMOND"));
        map.put("scannerAttributeMaxAbsolute", scanner.attributeMaxAbsolute());
        map.put("huskSyncEnabled", integrations.huskSyncEnabled());
        map.put("huskSyncTimeoutMillis", integrations.huskSyncTimeout().toMillis());
        map.put("reloadSuccessMessage", config.message("reload-success", ""));
        map.put("loggingEnabled", logging.enabled());
        map.put("loggingServerName", logging.serverName());
        map.put("loggingRetentionDays", logging.retentionDays());
        map.put("forensicWriterThreads", forensicWriterThreads());
        return Map.copyOf(map);
    }

    private static long forensicWriterThreads() {
        long count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("ItemGuard-ForensicLogWriter".equals(thread.getName()) && thread.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private PlayerDiagnosticSnapshot snapshot(UUID playerId, String name) {
        Instant now = Instant.now();
        Player online = Bukkit.getPlayer(playerId);
        PlayerGuardSession session = sessions.get(playerId);
        List<FlowView> flows = traces.recent(playerId, 80).stream().map(ItemGuardDiagnosticsImpl::flowView).toList();
        List<CreditView> credits = expectedFlows.ledger().snapshot(playerId, now).stream().map(ItemGuardDiagnosticsImpl::creditView).toList();
        List<AlertView> alertsForPlayer = alerts.recent().stream()
                .filter(alert -> playerId.equals(alert.playerId()))
                .map(ItemGuardDiagnosticsImpl::alertView)
                .toList();
        if (session == null) {
            return new PlayerDiagnosticSnapshot(
                    playerId,
                    name,
                    online != null,
                    false,
                    false,
                    false,
                    0L,
                    0L,
                    0,
                    "NONE",
                    "NONE",
                    null,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    flows,
                    List.of(),
                    credits,
                    alertsForPlayer,
                    0L,
                    0L,
                    0L,
                    null,
                    0,
                    detected(),
                    active()
            );
        }
        HuskSyncTransaction huskSync = session.huskSync();
        return new PlayerDiagnosticSnapshot(
                playerId,
                name,
                online != null,
                true,
                session.dirty(),
                session.reconcileScheduled(),
                session.lastReconcileTime() == null ? 0L : session.lastReconcileTime().toEpochMilli(),
                session.lastReconcileNanos(),
                session.currentRiskScore(),
                session.currentRiskLevel().name(),
                huskSync.state().name(),
                huskSync.transactionId() == null ? null : huskSync.transactionId().toString(),
                session.lastSnapshot() == null ? Map.of() : Map.copyOf(session.lastSnapshot().materialTotals()),
                session.lastSnapshot() == null ? Map.of() : Map.copyOf(session.lastSnapshot().nestedTotals()),
                Map.copyOf(session.lastDiff()),
                flows,
                List.copyOf(session.signals()).stream().map(signal -> signalView(signal, now)).toList(),
                credits,
                alertsForPlayer,
                session.lastSnapshot() == null ? 0L : session.lastSnapshot().timestamp().toEpochMilli(),
                huskSync.startedAt() == null ? 0L : huskSync.startedAt().toEpochMilli(),
                huskSync.completedAt() == null ? 0L : huskSync.completedAt().toEpochMilli(),
                huskSync.note(),
                huskSync.openCount(),
                detected(),
                active()
        );
    }

    private boolean detected() {
        return integrations != null && integrations.huskSyncDetected();
    }

    private boolean active() {
        return integrations != null && integrations.huskSyncActive();
    }

    private static PlayerDiagnosticSnapshot empty(UUID playerId, String name, boolean online) {
        return new PlayerDiagnosticSnapshot(
                playerId,
                name,
                online,
                false,
                false,
                false,
                0L,
                0L,
                0,
                "NONE",
                "NONE",
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0L,
                0L,
                0L,
                null,
                0,
                false,
                false
        );
    }

    private static FlowView flowView(ItemFlowEvent event) {
        return new FlowView(
                event.eventId(),
                event.timestamp().toEpochMilli(),
                event.material(),
                event.amountDelta(),
                event.source().name(),
                event.destination().name(),
                event.confidence().name(),
                event.worldName(),
                event.x(),
                event.y(),
                event.z(),
                event.container() == null ? null : event.container().format(),
                event.note()
        );
    }

    private static SignalView signalView(RiskSignal signal, Instant now) {
        return new SignalView(
                signal.signalId(),
                signal.type().name(),
                signal.scoreContribution(),
                signal.severity().name(),
                signal.material(),
                signal.description(),
                signal.timestamp().toEpochMilli(),
                signal.expiresAt().toEpochMilli(),
                signal.isExpired(now)
        );
    }

    private static CreditView creditView(ExpectedFlowCredit credit) {
        return new CreditView(
                credit.creditId(),
                credit.material(),
                credit.source().name(),
                credit.confidence().name(),
                credit.remainingAmount(),
                credit.oneShot(),
                credit.provider(),
                credit.createdAt().toEpochMilli(),
                credit.expiresAt().toEpochMilli(),
                credit.correlationId()
        );
    }

    private static AlertView alertView(AlertRecord alert) {
        return new AlertView(
                alert.alertId(),
                alert.playerName(),
                alert.riskScore(),
                alert.material(),
                alert.amount(),
                alert.sourceSummary(),
                alert.aggregatedEvents(),
                alert.firstSeen().toEpochMilli(),
                alert.lastSeen().toEpochMilli()
        );
    }

    private static ScanFindingView scanView(ScanFinding finding) {
        return new ScanFindingView(
                finding.ruleId(),
                finding.signalType().name(),
                finding.classification().name(),
                finding.severity().name(),
                finding.material(),
                finding.amount(),
                finding.description(),
                finding.evidence()
        );
    }
}
