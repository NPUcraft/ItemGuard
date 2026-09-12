package com.npucraft.itemguard.alert;

import com.npucraft.itemguard.config.AlertSettings;
import com.npucraft.itemguard.config.ConfigManager;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.log.ForensicLogPublisher;
import com.npucraft.itemguard.risk.incident.IncidentEscalation;
import com.npucraft.itemguard.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class AlertService {

    private final ConfigManager config;
    private final Logger logger;
    private final ForensicLogPublisher forensic;
    private final Deque<AlertRecord> recent = new ArrayDeque<>();
    private AlertAggregator aggregator;

    public AlertService(ConfigManager config, Logger logger, ForensicLogPublisher forensic) {
        this.config = config;
        this.logger = logger;
        this.forensic = forensic;
        rebuildAggregator();
    }

    public void rebuildAggregator() {
        AlertSettings settings = config.alertSettings();
        this.aggregator = new AlertAggregator(
                settings.aggregationWindow().plus(config.riskSettings().alertCooldown()),
                settings.aggregationWindow(),
                settings.criticalThreshold()
        );
    }

    public void onReconciliation(ReconciliationService.ReconciliationResult result) {
        if (result == null || result.escalations() == null || result.escalations().isEmpty()) {
            return;
        }
        AlertSettings settings = config.alertSettings();
        if (!settings.enabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(result.playerId());
        String name = player == null ? result.playerId().toString() : player.getName();
        Instant now = Instant.now();
        for (IncidentEscalation escalation : result.escalations()) {
            if (!escalation.isStaffAlert()) {
                continue;
            }
            if ("*".equals(escalation.material()) && escalation.amount() <= 0 && (escalation.evidence() == null || escalation.evidence().isEmpty())) {
                continue;
            }
            List<String> reasons = escalation.evidence().isEmpty()
                    ? List.of(escalation.type().name())
                    : escalation.evidence();
            AlertRecord record = new AlertRecord(
                    UUID.randomUUID(),
                    result.playerId(),
                    name,
                    escalation.score(),
                    escalation.material(),
                    escalation.amount(),
                    escalation.sourceSummary(),
                    reasons,
                    1,
                    now,
                    now,
                    escalation.correlationId(),
                    escalation.incidentId(),
                    escalation.type().name(),
                    escalation.transitionName()
            );
            emit(record);
        }
    }

    public List<AlertRecord> recent() {
        return List.copyOf(recent);
    }

    AlertAggregator aggregator() {
        return aggregator;
    }

    private void emit(AlertRecord record) {
        recent.addLast(record);
        while (recent.size() > 50) {
            recent.removeFirst();
        }
        AlertSettings settings = config.alertSettings();
        if (settings.console()) {
            logger.info("Alert " + record.playerName()
                    + " incident=" + shortIncident(record)
                    + " risk=" + record.riskScore()
                    + " " + record.material()
                    + " +" + record.amount());
        }
        if (settings.adminChat()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(settings.permission())) {
                    staff.sendMessage(render(record, staff));
                }
            }
        }
        if (forensic != null) {
            forensic.onAlert(record);
        }
    }

    private Component render(AlertRecord record, Player staff) {
        Map<String, String> placeholders = Map.of(
                "player", record.playerName(),
                "item", record.material(),
                "amount", Integer.toString(record.amount()),
                "window", window(record),
                "source", record.sourceSummary() == null ? "unknown" : record.sourceSummary(),
                "risk", Integer.toString(record.riskScore()),
                "reason", String.join("\n", record.reasons()),
                "incident", shortIncident(record),
                "level", levelLabel(record.riskScore())
        );
        Component title = Texts.parse(config.message("alert-title", "<red>⚠ ItemGuard Alert"));
        Component body = Texts.parse(config.message("alert-body", """
                <gray>Player: <white><player></white>
                Incident: <white><incident></white>
                Risk: <gold><risk></gold><gray>/100 <level></gray>
                Item: <white><item></white> <green>+<amount></green>
                Source: <white><source></white>
                Evidence:
                <white><reason></white>
                """), placeholders);
        Component buttons = Component.newline();
        AlertSettings settings = config.alertSettings();
        if (settings.inspectButton()) {
            buttons = buttons.append(button(
                    config.message("alert-inspect", "<aqua>[Inspect]"),
                    config.message("alert-inspect-hover", "Run /ig inspect <player>").replace("<player>", record.playerName()),
                    "/itemguard inspect " + record.playerName()
            )).append(Component.space());
        }
        if (settings.traceButton()) {
            buttons = buttons.append(button(
                    config.message("alert-trace", "<yellow>[Trace]"),
                    config.message("alert-trace-hover", "Run /ig trace <player> 5m").replace("<player>", record.playerName()),
                    "/itemguard trace " + record.playerName() + " " + settings.traceDuration()
            )).append(Component.space());
        }
        if (settings.teleportButton() && canTeleport(staff)) {
            buttons = buttons.append(button(
                    config.message("alert-teleport", "<green>[Teleport]"),
                    config.message("alert-teleport-hover", "Teleport to <player>").replace("<player>", record.playerName()),
                    "/tp " + record.playerName()
            ));
        }
        return Component.text()
                .append(title)
                .append(Component.newline())
                .append(body)
                .append(buttons)
                .colorIfAbsent(NamedTextColor.GRAY)
                .build();
    }

    private static boolean canTeleport(Player staff) {
        return staff.hasPermission("minecraft.command.teleport")
                || staff.hasPermission("minecraft.command.tp")
                || staff.hasPermission("bukkit.command.teleport");
    }

    private static Component button(String label, String hover, String command) {
        return Texts.parse(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private String levelLabel(int score) {
        return config.riskSettings().levelFor(score).name();
    }

    private static String shortIncident(AlertRecord record) {
        if (record.incidentId() == null) {
            return "-";
        }
        String raw = record.incidentId().toString().replace("-", "");
        return "#" + raw.substring(0, Math.min(8, raw.length())).toUpperCase();
    }

    private static String window(AlertRecord record) {
        Duration duration = Duration.between(record.firstSeen(), record.lastSeen());
        if (duration.isZero()) {
            return "now";
        }
        return (duration.toMillis() / 1000.0) + "s";
    }
}
