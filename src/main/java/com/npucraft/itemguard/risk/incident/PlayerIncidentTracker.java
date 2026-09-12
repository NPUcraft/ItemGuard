package com.npucraft.itemguard.risk.incident;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player active incidents. Player current risk is {@code MAX(active incident scores)}.
 */
public final class PlayerIncidentTracker {

    private final Map<UUID, List<RiskIncident>> incidents = new ConcurrentHashMap<>();

    public List<IncidentEscalation> ingest(UUID playerId, List<RiskSignal> incoming, Instant now, RiskSettings settings) {
        purge(playerId, now);
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        Set<RiskIncident> touched = new HashSet<>();
        for (RiskSignal signal : incoming) {
            if (signal == null || signal.isExpired(now) || signal.scoreContribution() <= 0) {
                continue;
            }
            IncidentType type = typeOf(signal.type());
            if (type == null) {
                continue;
            }
            RiskIncident incident = findOrCreate(playerId, signal, type, now, settings);
            boolean newType = incident.evidence().stream().noneMatch(existing -> existing.type() == signal.type());
            boolean higherScore = incident.evidence().stream()
                    .filter(existing -> existing.type() == signal.type())
                    .anyMatch(existing -> signal.scoreContribution() > existing.scoreContribution());
            incident.addEvidence(signal, now, RiskIncident.ttlFor(type, settings.incidents()), newType || higherScore);
            touched.add(incident);
        }
        cap(playerId, now, settings.incidents().maxActivePerPlayer());
        List<IncidentEscalation> escalations = new ArrayList<>();
        for (RiskIncident incident : touched) {
            IncidentEscalation escalation = maybeEscalate(incident, settings);
            if (escalation != null) {
                escalations.add(escalation);
            }
        }
        return List.copyOf(escalations);
    }

    public int currentRisk(UUID playerId, Instant now) {
        return active(playerId, now).stream().mapToInt(RiskIncident::score).max().orElse(0);
    }

    public RiskIncident highest(UUID playerId, Instant now) {
        return active(playerId, now).stream().max(Comparator.comparingInt(RiskIncident::score)).orElse(null);
    }

    public List<RiskIncident> active(UUID playerId, Instant now) {
        purge(playerId, now);
        List<RiskIncident> list = incidents.get(playerId);
        if (list == null) {
            return List.of();
        }
        return list.stream().filter(incident -> incident.isActive(now)).toList();
    }

    public List<RiskSignal> activeEvidence(UUID playerId, Instant now) {
        List<RiskSignal> evidence = new ArrayList<>();
        for (RiskIncident incident : active(playerId, now)) {
            evidence.addAll(incident.evidence());
        }
        return List.copyOf(evidence);
    }

    public List<RiskIncidentSnapshot> snapshots(UUID playerId, Instant now, RiskSettings settings) {
        return active(playerId, now).stream().map(incident -> incident.snapshot(settings)).toList();
    }

    public void resolveScannerFinding(UUID playerId, IncidentType type, String discriminator, Instant now) {
        List<RiskIncident> list = incidents.get(playerId);
        if (list == null) {
            return;
        }
        IncidentKey key = IncidentKey.scanner(type, discriminator, discriminator.contains("|")
                ? discriminator.substring(discriminator.indexOf('|') + 1)
                : discriminator);
        for (RiskIncident incident : list) {
            if (incident.key().equals(key) || incident.key().discriminator().equals(key.discriminator())) {
                incident.resolve(now);
            }
        }
    }

    public void resolveByFindingKey(UUID playerId, String findingKey, Instant now) {
        List<RiskIncident> list = incidents.get(playerId);
        if (list == null) {
            return;
        }
        for (RiskIncident incident : list) {
            if (incident.type() == IncidentType.ILLEGAL_ITEM || incident.type() == IncidentType.SUSPICIOUS_ITEM) {
                if (incident.key().discriminator().equalsIgnoreCase(findingKey)) {
                    incident.resolve(now);
                }
            }
        }
    }

    public void clear(UUID playerId) {
        incidents.remove(playerId);
    }

    public void clearAll() {
        incidents.clear();
    }

    public void purge(UUID playerId, Instant now) {
        List<RiskIncident> list = incidents.get(playerId);
        if (list == null) {
            return;
        }
        Iterator<RiskIncident> iterator = list.iterator();
        while (iterator.hasNext()) {
            RiskIncident incident = iterator.next();
            if (incident.state() == IncidentState.ACTIVE && !now.isBefore(incident.expiresAt())) {
                incident.expire(now);
            }
            if (incident.state() != IncidentState.ACTIVE) {
                Duration keep = Duration.ofSeconds(30);
                if (incident.lastUpdatedAt().plus(keep).isBefore(now)) {
                    iterator.remove();
                }
            }
        }
        if (list.isEmpty()) {
            incidents.remove(playerId);
        }
    }

    private RiskIncident findOrCreate(
            UUID playerId,
            RiskSignal signal,
            IncidentType type,
            Instant now,
            RiskSettings settings
    ) {
        IncidentKey key = keyOf(signal, type);
        List<RiskIncident> list = incidents.computeIfAbsent(playerId, unused -> new ArrayList<>());
        for (RiskIncident incident : list) {
            if (!incident.isActive(now) || !incident.key().equals(key)) {
                continue;
            }
            if (type != IncidentType.ITEM_GAIN || sameItemGainWave(incident, signal, now, settings.incidents().itemGainJoin())) {
                return incident;
            }
        }
        if (type == IncidentType.ITEM_GAIN && (signal.material() == null || "*".equals(signal.material()))) {
            RiskIncident hottest = list.stream()
                    .filter(incident -> incident.isActive(now) && incident.type() == IncidentType.ITEM_GAIN)
                    .filter(incident -> sameItemGainWave(incident, signal, now, settings.incidents().itemGainJoin()))
                    .max(Comparator.comparingInt(RiskIncident::score))
                    .orElse(null);
            if (hottest != null) {
                return hottest;
            }
        }
        RiskIncident created = new RiskIncident(
                playerId,
                type,
                key,
                now,
                RiskIncident.ttlFor(type, settings.incidents()),
                null
        );
        list.add(created);
        return created;
    }

    private void cap(UUID playerId, Instant now, int max) {
        List<RiskIncident> list = incidents.get(playerId);
        if (list == null || list.size() <= max) {
            return;
        }
        List<RiskIncident> active = list.stream().filter(incident -> incident.isActive(now)).sorted(
                Comparator.comparingInt(RiskIncident::score).thenComparing(RiskIncident::lastUpdatedAt)
        ).toList();
        int extra = active.size() - max;
        for (int i = 0; i < extra; i++) {
            active.get(i).expire(now);
        }
    }

    private static IncidentEscalation maybeEscalate(RiskIncident incident, RiskSettings settings) {
        IncidentAlertLevel current = levelFor(incident.score(), settings);
        IncidentAlertLevel previous = incident.highestAlertedLevel();
        if (current.ordinal() <= previous.ordinal() || current == IncidentAlertLevel.NONE) {
            return null;
        }
        incident.setHighestAlertedLevel(current);
        RiskSignal primary = primaryEvidence(incident);
        return new IncidentEscalation(
                incident.incidentId(),
                incident.type(),
                previous,
                current,
                incident.score(),
                incident.material(),
                primary == null ? 0 : primary.amount(),
                primary == null || primary.evidence().get("source") == null ? "UNKNOWN" : primary.evidence().get("source"),
                incident.evidence().stream().map(RiskSignal::description).toList(),
                incident.primaryCorrelation()
        );
    }

    private static RiskSignal primaryEvidence(RiskIncident incident) {
        return incident.evidence().stream()
                .max(Comparator.comparingInt(RiskSignal::scoreContribution))
                .orElse(null);
    }

    static IncidentAlertLevel levelFor(int score, RiskSettings settings) {
        if (score >= settings.criticalThreshold()) {
            return IncidentAlertLevel.CRITICAL;
        }
        if (score >= settings.highThreshold()) {
            return IncidentAlertLevel.HIGH;
        }
        return IncidentAlertLevel.NONE;
    }

    static IncidentType typeOf(SignalType type) {
        return switch (type) {
            case UNEXPLAINED_ITEM_GAIN, HIGH_VALUE_ITEM_BURST, RARE_ITEM_BURST,
                    REPEATED_IDENTICAL_ITEMS, RAPID_ITEM_GAIN -> IncidentType.ITEM_GAIN;
            case SHULKER_HIGH_FLOW, SHULKER_RAPID_TRANSFER -> IncidentType.SHULKER_ACTIVITY;
            case ILLEGAL_ENCHANTMENT, OVER_LEVEL_ENCHANTMENT, OVERSIZED_STACK, ABNORMAL_DURABILITY
                    -> IncidentType.ILLEGAL_ITEM;
            case CONFLICTING_ENCHANTMENTS, SUSPICIOUS_ATTRIBUTE, SUSPICIOUS_COMPONENT,
                    SUSPICIOUS_CONTAINER_CONTENTS -> IncidentType.SUSPICIOUS_ITEM;
            default -> null;
        };
    }

    static IncidentKey keyOf(RiskSignal signal, IncidentType type) {
        return switch (type) {
            case ITEM_GAIN -> IncidentKey.itemGain(signal.material());
            case SHULKER_ACTIVITY -> IncidentKey.shulker();
            case ILLEGAL_ITEM, SUSPICIOUS_ITEM -> IncidentKey.scanner(
                    type,
                    signaturePart(signal.signature(), signal.material()),
                    signal.type().name()
            );
        };
    }

    static String signaturePart(ItemSignature signature, String material) {
        if (signature != null && signature.isDetailed()) {
            return signature.hash();
        }
        return material == null ? "*" : material;
    }

    public static String findingDiscriminator(ItemSignature signature, String material, SignalType type) {
        return signaturePart(signature, material) + "|" + type.name();
    }

    private static boolean sameItemGainWave(RiskIncident incident, RiskSignal signal, Instant now, Duration join) {
        if (signal.correlationId() != null && signal.correlationId().equals(incident.primaryCorrelation())) {
            return true;
        }
        Duration window = join == null ? Duration.ofMillis(2_000) : join;
        return !incident.lastUpdatedAt().plus(window).isBefore(now);
    }
}
