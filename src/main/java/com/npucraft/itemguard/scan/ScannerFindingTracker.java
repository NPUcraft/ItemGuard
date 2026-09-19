package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.incident.PlayerIncidentTracker;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent scanner findings keyed by player + item signature + finding type.
 * Repeat scans refresh lastSeen/slot and must not create a new risk contribution.
 */
public final class ScannerFindingTracker {

    private static final int MAX_PER_PLAYER = 64;
    private static final Duration RETENTION = Duration.ofMinutes(2);

    private final Map<UUID, Map<String, FindingState>> findings = new ConcurrentHashMap<>();

    public record ObserveResult(boolean newRiskEvidence, FindingLifecycle lifecycle, FindingState state) {
        public ObserveResult(boolean newRiskEvidence, FindingState state) {
            this(newRiskEvidence, newRiskEvidence ? FindingLifecycle.NEW : FindingLifecycle.REFRESH, state);
        }
    }

    public ObserveResult observe(UUID playerId, ScanFinding finding, int slot, Instant now) {
        if (playerId == null || finding == null) {
            return new ObserveResult(false, FindingLifecycle.REFRESH, null);
        }
        if (finding.classification() == ScanClassification.INFO
                || finding.classification() == ScanClassification.CUSTOM) {
            return new ObserveResult(false, FindingLifecycle.REFRESH, null);
        }
        String key = key(finding.signature(), finding.material(), finding.signalType());
        Map<String, FindingState> byKey = findings.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
        FindingState existing = byKey.get(key);
        if (existing != null && existing.active) {
            existing.lastSeen = now;
            existing.slot = slot;
            existing.seenThisScan = true;
            return new ObserveResult(false, FindingLifecycle.REFRESH, existing);
        }
        cap(byKey);
        FindingState created = new FindingState(
                key,
                finding.signalType(),
                now,
                slot,
                finding.material(),
                finding.ruleId(),
                ScannerFindingLog.boundedReason(finding.description())
        );
        created.seenThisScan = true;
        byKey.put(key, created);
        return new ObserveResult(true, FindingLifecycle.NEW, created);
    }

    public void beginScan(UUID playerId) {
        Map<String, FindingState> byKey = findings.get(playerId);
        if (byKey == null) {
            return;
        }
        for (FindingState state : byKey.values()) {
            state.seenThisScan = false;
        }
    }

    public List<FindingState> finishScan(UUID playerId, Instant now, PlayerIncidentTracker incidents) {
        Map<String, FindingState> byKey = findings.get(playerId);
        if (byKey == null) {
            return List.of();
        }
        List<FindingState> resolved = new ArrayList<>();
        Iterator<Map.Entry<String, FindingState>> iterator = byKey.entrySet().iterator();
        while (iterator.hasNext()) {
            FindingState state = iterator.next().getValue();
            if (state.active && !state.seenThisScan) {
                state.active = false;
                state.resolvedAt = now;
                resolved.add(state);
                if (incidents != null) {
                    incidents.resolveByFindingKey(playerId, state.key, now);
                }
            }
            if (!state.active && state.resolvedAt != null && state.resolvedAt.plus(RETENTION).isBefore(now)) {
                iterator.remove();
            }
        }
        if (byKey.isEmpty()) {
            findings.remove(playerId);
        }
        return List.copyOf(resolved);
    }

    public void clear(UUID playerId) {
        findings.remove(playerId);
    }

    public int activeCount(UUID playerId) {
        Map<String, FindingState> byKey = findings.get(playerId);
        if (byKey == null) {
            return 0;
        }
        int count = 0;
        for (FindingState state : byKey.values()) {
            if (state.active) {
                count++;
            }
        }
        return count;
    }

    public static String key(ItemSignature signature, String material, SignalType type) {
        return PlayerIncidentTracker.findingDiscriminator(signature, material, type);
    }

    private static void cap(Map<String, FindingState> byKey) {
        if (byKey.size() < MAX_PER_PLAYER) {
            return;
        }
        FindingState oldest = null;
        for (FindingState state : byKey.values()) {
            if (!state.active && (oldest == null || state.lastSeen.isBefore(oldest.lastSeen))) {
                oldest = state;
            }
        }
        if (oldest != null) {
            byKey.remove(oldest.key);
        }
    }

    public static final class FindingState {
        private final String key;
        private final SignalType type;
        private final Instant firstSeen;
        private Instant lastSeen;
        private Instant resolvedAt;
        private int slot;
        private boolean active = true;
        private boolean seenThisScan;
        private final String material;
        private final String ruleId;
        private final String triggerReason;

        private FindingState(
                String key,
                SignalType type,
                Instant now,
                int slot,
                String material,
                String ruleId,
                String triggerReason
        ) {
            this.key = key;
            this.type = type;
            this.firstSeen = now;
            this.lastSeen = now;
            this.slot = slot;
            this.material = material == null ? "*" : material;
            this.ruleId = ruleId == null ? "" : ruleId;
            this.triggerReason = triggerReason == null ? "" : triggerReason;
        }

        public String key() {
            return key;
        }

        public SignalType type() {
            return type;
        }

        public Instant firstSeen() {
            return firstSeen;
        }

        public Instant lastSeen() {
            return lastSeen;
        }

        public int slot() {
            return slot;
        }

        public boolean active() {
            return active;
        }

        public String material() {
            return material;
        }

        public String ruleId() {
            return ruleId;
        }

        public String triggerReason() {
            return triggerReason;
        }
    }
}
