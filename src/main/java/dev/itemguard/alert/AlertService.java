package dev.itemguard.alert;

import dev.itemguard.config.AlertSettings;
import dev.itemguard.config.ConfigManager;
import dev.itemguard.log.ForensicLogPublisher;
import dev.itemguard.flow.ReconciliationService;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.util.Texts;
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
        if (result == null || result.assessment() == null) {
            return;
        }
        AlertSettings settings = config.alertSettings();
        if (!settings.enabled() || result.assessment().score() < settings.threshold()) {
            return;
        }
        Player player = Bukkit.getPlayer(result.playerId());
        String name = player == null ? result.playerId().toString() : player.getName();
        ItemFlowEvent primary = primaryFlow(result.flows());
        String material = primary == null ? "*" : primary.material();
        int amount = primary == null ? 0 : primary.amountDelta();
        String source = primary == null ? "unknown" : sourceSummary(primary);
        List<String> reasons = result.signals().stream().map(RiskSignal::description).distinct().toList();
        if (reasons.isEmpty()) {
            reasons = List.of("Risk threshold reached");
        }
        AlertKey key = new AlertKey(result.playerId(), primary == null ? "RISK" : primary.source().name(), material);
        aggregator.publish(key, name, result.assessment().score(), amount, source, reasons, primary == null ? null : primary.correlationId(), Instant.now())
                .ifPresent(this::emit);
    }

    public List<AlertRecord> recent() {
        return List.copyOf(recent);
    }

    private void emit(AlertRecord record) {
        recent.addLast(record);
        while (recent.size() > 50) {
            recent.removeFirst();
        }
        AlertSettings settings = config.alertSettings();
        if (settings.console()) {
            logger.info("Alert " + record.playerName() + " risk=" + record.riskScore() + " " + record.material() + " +" + record.amount());
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
                "source", record.sourceSummary(),
                "risk", Integer.toString(record.riskScore()),
                "reason", String.join("\n", record.reasons())
        );
        Component title = Texts.parse(config.message("alert-title", "<red>⚠ ItemGuard Alert"));
        Component body = Texts.parse(config.message("alert-body", "<gray>Player: <white><player>"), placeholders);
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

    private static ItemFlowEvent primaryFlow(List<ItemFlowEvent> flows) {
        return flows.stream().filter(ItemFlowEvent::isUnexplained).findFirst()
                .or(() -> flows.stream().filter(ItemFlowEvent::isGain).findFirst())
                .orElse(flows.isEmpty() ? null : flows.get(0));
    }

    private static String sourceSummary(ItemFlowEvent flow) {
        if (flow.container() != null) {
            return flow.container().format() + " → " + flow.destination().name();
        }
        return flow.source().name() + " → " + flow.destination().name();
    }

    private static String window(AlertRecord record) {
        Duration duration = Duration.between(record.firstSeen(), record.lastSeen());
        if (duration.isZero()) {
            return "now";
        }
        return (duration.toMillis() / 1000.0) + "s";
    }
}
