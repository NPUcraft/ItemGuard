package com.npucraft.itemguard.log;

import com.npucraft.itemguard.alert.AlertRecord;
import com.npucraft.itemguard.config.ConfigManager;
import com.npucraft.itemguard.config.LoggingSettings;
import com.npucraft.itemguard.flow.ReconciliationService;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.RiskLevel;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ScanClassification;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps detection results onto forensic records. Never participates in scoring.
 */
public final class ForensicLogPublisher {

    private static final int TOP_CHANGE_LIMIT = 8;

    private final ConfigManager config;
    private volatile ForensicLogSink sink;

    public ForensicLogPublisher(ConfigManager config, ForensicLogSink sink) {
        this.config = config;
        this.sink = sink;
    }

    public void updateSink(ForensicLogSink sink) {
        this.sink = sink == null ? NoOpForensicLogSink.INSTANCE : sink;
    }

    public ForensicLogSink sink() {
        return sink;
    }

    public void onReconciliation(ReconciliationService.ReconciliationResult result) {
        ForensicLogSink current = sink;
        if (result == null || current == null || !current.enabled()) {
            return;
        }
        try {
            publishReconciliation(result, current);
        } catch (RuntimeException ignored) {
            // Detection must continue even if forensic mapping fails.
        }
    }

    public void onAlert(AlertRecord record) {
        ForensicLogSink current = sink;
        LoggingSettings logging = config.loggingSettings();
        if (record == null || current == null || !current.enabled() || !logging.logAlerts()) {
            return;
        }
        try {
            RiskLevel level = config.riskSettings().levelFor(record.riskScore());
            ForensicLogPriority priority = level == RiskLevel.CRITICAL
                    ? ForensicLogPriority.CRITICAL
                    : (level == RiskLevel.HIGH ? ForensicLogPriority.HIGH : ForensicLogPriority.NORMAL);
            current.submit(ForensicLogRecord.builder(ForensicLogType.ALERT, priority)
                    .instant(record.lastSeen() == null ? Instant.now() : record.lastSeen())
                    .serverName(logging.serverName())
                    .player(record.playerId(), record.playerName())
                    .material(record.material())
                    .amount(record.amount())
                    .source(record.sourceSummary())
                    .risk(record.riskScore(), level.name())
                    .correlationId(record.correlationId())
                    .incident(record.incidentId(), record.incidentType(), record.riskScore(), record.alertTransition())
                    .summary("ItemGuard alert")
                    .metadata(Map.of(
                            "aggregatedEvents", Integer.toString(record.aggregatedEvents()),
                            "reasons", String.join("; ", record.reasons())
                    ))
                    .build());
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    public void onAdminAction(CommandSender sender, String action, UUID targetId, String targetName, String result) {
        LoggingSettings logging = config.loggingSettings();
        ForensicLogSink current = sink;
        if (current == null || !current.enabled() || !logging.logAdminActions()) {
            return;
        }
        try {
            UUID adminId = sender instanceof Player player ? player.getUniqueId() : null;
            String adminName = sender.getName();
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("action", action);
            metadata.put("result", result);
            if (targetId != null) {
                metadata.put("targetUuid", targetId.toString());
            }
            if (targetName != null) {
                metadata.put("target", targetName);
            }
            current.submit(ForensicLogRecord.builder(ForensicLogType.ADMIN_ACTION, ForensicLogPriority.NORMAL)
                    .serverName(logging.serverName())
                    .player(adminId, adminName)
                    .provider("ItemGuard")
                    .summary(action + (targetName == null ? "" : " " + targetName))
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void publishReconciliation(ReconciliationService.ReconciliationResult result, ForensicLogSink current) {
        LoggingSettings logging = config.loggingSettings();
        ItemValueRegistry values = config.itemValues();
        boolean huskSyncApply = "husksync-complete".equals(result.reason());
        if (huskSyncApply && logging.logHuskSync()) {
            current.submit(huskSyncSummary(result, logging));
        }
        for (ItemFlowEvent flow : result.flows()) {
            if (!huskSyncApply && flow.isUnexplained() && flow.isGain() && logging.logUnknownGains()) {
                current.submit(unknownGain(flow, result, logging, values));
            }
            if (!huskSyncApply && logging.logHighValueFlows() && isHighValueFlow(flow, values, logging)) {
                current.submit(highValueFlow(flow, result, logging));
            }
        }
        for (RiskSignal signal : result.signals()) {
            if (!isScannerSignal(signal.type())) {
                continue;
            }
            ForensicLogRecord finding = scannerFinding(signal, result, logging);
            if (finding != null) {
                current.submit(finding);
            }
        }
    }

    private boolean isHighValueFlow(ItemFlowEvent flow, ItemValueRegistry values, LoggingSettings logging) {
        if (flow == null || !flow.isGain()) {
            return false;
        }
        if (flow.source() == FlowSource.HUSKSYNC_DATA_APPLY
                || flow.source() == FlowSource.CREATIVE_INVENTORY
                || flow.source() == FlowSource.UNKNOWN) {
            return false;
        }
        return values.valueOf(flow.material()) >= logging.highValueMinimum();
    }

    private ForensicLogRecord unknownGain(
            ItemFlowEvent flow,
            ReconciliationService.ReconciliationResult result,
            LoggingSettings logging,
            ItemValueRegistry values
    ) {
        ForensicLogPriority priority = unknownPriority(flow, values);
        int contribution = 0;
        if (result.signals() != null) {
            contribution = result.signals().stream()
                    .filter(signal -> signal.type() == SignalType.UNEXPLAINED_ITEM_GAIN && flow.material().equals(signal.material()))
                    .mapToInt(RiskSignal::scoreContribution)
                    .max()
                    .orElse(0);
        }
        Integer risk = result.assessment() == null ? contribution : result.assessment().score();
        String level = result.assessment() == null ? RiskLevel.SUSPICIOUS.name() : result.assessment().level().name();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("riskContribution", Integer.toString(contribution));
        if (result.attribution() != null) {
            metadata.putAll(result.attribution().toMetadata());
        }
        ForensicLogRecord.Builder builder = baseFlow(ForensicLogType.UNKNOWN_GAIN, priority, flow, logging)
                .risk(risk, level)
                .source("UNKNOWN")
                .sourceConfidence(flow.confidence().name())
                .summary("Unexplained inventory gain")
                .metadata(metadata);
        if (result.assessment() != null) {
            builder.incident(
                    result.assessment().highestIncidentId(),
                    result.assessment().highestIncidentType(),
                    result.assessment().score(),
                    null
            );
        }
        return builder.build();
    }

    private ForensicLogRecord highValueFlow(
            ItemFlowEvent flow,
            ReconciliationService.ReconciliationResult result,
            LoggingSettings logging
    ) {
        Integer risk = result.assessment() == null ? 0 : result.assessment().score();
        String level = result.assessment() == null ? RiskLevel.NORMAL.name() : result.assessment().level().name();
        return baseFlow(ForensicLogType.HIGH_VALUE_FLOW, ForensicLogPriority.NORMAL, flow, logging)
                .risk(risk, level)
                .summary("High-value item flow")
                .build();
    }

    private ForensicLogRecord huskSyncSummary(ReconciliationService.ReconciliationResult result, LoggingSettings logging) {
        Map<String, Integer> delta = result.delta() == null ? Map.of() : result.delta();
        int incoming = 0;
        int outgoing = 0;
        List<String> top = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : delta.entrySet()) {
            int change = entry.getValue();
            if (change > 0) {
                incoming += change;
            } else if (change < 0) {
                outgoing += -change;
            }
            if (top.size() < TOP_CHANGE_LIMIT && change != 0) {
                top.add(entry.getKey() + ":" + (change > 0 ? "+" : "") + change);
            }
        }
        ItemFlowEvent sample = result.flows().stream().filter(ItemFlowEvent::isGain).findFirst().orElse(
                result.flows().isEmpty() ? null : result.flows().get(0)
        );
        UUID playerId = result.playerId();
        String playerName = sample == null ? null : sample.playerName();
        UUID correlation = sample == null ? null : sample.correlationId();
        ForensicLogRecord.Builder builder = ForensicLogRecord.builder(ForensicLogType.HUSKSYNC_DATA_APPLY, ForensicLogPriority.NORMAL)
                .instant(sample == null ? Instant.now() : sample.timestamp())
                .serverName(logging.serverName())
                .player(playerId, playerName)
                .source(FlowSource.HUSKSYNC_DATA_APPLY.name())
                .destination("PLAYER_INVENTORY")
                .sourceConfidence("TRUSTED")
                .risk(0, RiskLevel.NORMAL.name())
                .correlationId(correlation)
                .summary("HuskSync inventory data apply")
                .metadata(Map.of(
                        "changedMaterialsCount", Integer.toString(delta.size()),
                        "totalIncomingItems", Integer.toString(incoming),
                        "totalOutgoingItems", Integer.toString(outgoing),
                        "topChanges", String.join(",", top)
                ));
        if (sample != null) {
            builder.location(sample.worldId(), sample.worldName(), sample.x(), sample.y(), sample.z());
        }
        return builder.build();
    }

    private ForensicLogRecord scannerFinding(
            RiskSignal signal,
            ReconciliationService.ReconciliationResult result,
            LoggingSettings logging
    ) {
        ScanClassification classification = classificationOf(signal.severity());
        if (classification == ScanClassification.INFO || classification == ScanClassification.CUSTOM) {
            return null;
        }
        if (classification == ScanClassification.INVALID && !logging.logInvalidItems()) {
            return null;
        }
        if (classification == ScanClassification.SUSPICIOUS) {
            if (!logging.logSuspiciousItems()) {
                return null;
            }
            if (signal.severity() != Severity.HIGH && signal.severity() != Severity.CRITICAL && signal.severity() != Severity.SUSPICIOUS) {
                return null;
            }
        }
        ForensicLogType type = classification == ScanClassification.INVALID
                ? ForensicLogType.INVALID_ITEM
                : ForensicLogType.SUSPICIOUS_ITEM;
        ForensicLogPriority priority = classification == ScanClassification.INVALID
                ? ForensicLogPriority.CRITICAL
                : ForensicLogPriority.HIGH;
        ItemFlowEvent sample = result.flows().isEmpty() ? null : result.flows().get(0);
        String playerName = sample == null ? null : sample.playerName();
        ForensicLogRecord.Builder builder = ForensicLogRecord.builder(type, priority)
                .instant(signal.timestamp())
                .serverName(logging.serverName())
                .player(signal.playerId(), playerName)
                .material(signal.material())
                .amount(signal.amount())
                .classification(classification.name())
                .findingTypes(List.of(signal.type().name()))
                .signalTypes(List.of(signal.type().name()))
                .correlationId(signal.correlationId())
                .itemSignature(signatureHash(signal.signature()))
                .risk(signal.scoreContribution(), classification.name())
                .summary(signal.description())
                .metadata(safeEvidence(signal.evidence()));
        if (sample != null) {
            builder.location(sample.worldId(), sample.worldName(), sample.x(), sample.y(), sample.z());
        }
        return builder.build();
    }

    private ForensicLogRecord.Builder baseFlow(
            ForensicLogType type,
            ForensicLogPriority priority,
            ItemFlowEvent flow,
            LoggingSettings logging
    ) {
        return ForensicLogRecord.builder(type, priority)
                .instant(flow.timestamp())
                .serverName(logging.serverName())
                .player(flow.playerId(), flow.playerName())
                .material(flow.material())
                .amount(flow.amountDelta())
                .source(flow.source().name())
                .destination(flow.destination().name())
                .sourceConfidence(flow.confidence().name())
                .correlationId(flow.correlationId())
                .itemSignature(signatureHash(flow.signature()))
                .location(flow.worldId(), flow.worldName(), flow.x(), flow.y(), flow.z());
    }

    private ForensicLogPriority unknownPriority(ItemFlowEvent flow, ItemValueRegistry values) {
        int weight = values.valueOf(flow.material());
        if (weight >= 30 && flow.amountDelta() >= 64) {
            return ForensicLogPriority.CRITICAL;
        }
        if (weight >= 30) {
            return ForensicLogPriority.HIGH;
        }
        return ForensicLogPriority.NORMAL;
    }

    private static boolean isScannerSignal(SignalType type) {
        return type == SignalType.ILLEGAL_ENCHANTMENT
                || type == SignalType.OVER_LEVEL_ENCHANTMENT
                || type == SignalType.CONFLICTING_ENCHANTMENTS
                || type == SignalType.OVERSIZED_STACK
                || type == SignalType.SUSPICIOUS_ATTRIBUTE
                || type == SignalType.SUSPICIOUS_COMPONENT
                || type == SignalType.COMPONENT_MODIFIED
                || type == SignalType.CUSTOM_ITEM_METADATA
                || type == SignalType.ABNORMAL_DURABILITY
                || type == SignalType.SUSPICIOUS_CONTAINER_CONTENTS
                || type == SignalType.CUSTOM_MAX_STACK;
    }

    private static ScanClassification classificationOf(Severity severity) {
        return switch (severity) {
            case INVALID, CRITICAL -> ScanClassification.INVALID;
            case HIGH, SUSPICIOUS -> ScanClassification.SUSPICIOUS;
            case CUSTOM -> ScanClassification.CUSTOM;
            case INFO -> ScanClassification.INFO;
        };
    }

    private static String signatureHash(ItemSignature signature) {
        if (signature == null || !signature.isDetailed()) {
            return null;
        }
        return signature.hash();
    }

    private static Map<String, String> safeEvidence(Map<String, String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        evidence.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String lower = key.toLowerCase();
            if (lower.contains("password") || lower.contains("secret") || lower.contains("nbt")
                    || lower.contains("serialized") || lower.contains("pdcvalue") || lower.equals("ip")) {
                return;
            }
            copy.put(key, value == null ? "" : value);
        });
        return copy;
    }
}
