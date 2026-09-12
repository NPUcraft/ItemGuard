package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.incident.IncidentType;
import com.npucraft.itemguard.risk.incident.PlayerIncidentTracker;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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

    public record ObserveResult(boolean newRiskEvidence, FindingState state) {
    }

    public ObserveResult observe(UUID playerId, ScanFinding finding, int slot, Instant now) {
        if (playerId == null || finding == null) {
            return new ObserveResult(false, null);
        }
        if (finding.classification() == ScanClassification.INFO
                || finding.classification() == ScanClassification.CUSTOM) {
            return new ObserveResult(false, null);
        }
        String key = key(finding.signature(), finding.material(), finding.signalType());
        Map<String, FindingState> byKey = findings.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
        FindingState existing = byKey.get(key);
        if (existing != null && existing.active) {
            existing.lastSeen = now;
            existing.slot = slot;
            existing.seenThisScan = true;
            return new ObserveResult(false, existing);
        }
        cap(byKey);
        FindingState created = new FindingState(key, finding.signalType(), now, slot, incidentType(finding));
        created.seenThisScan = true;
        byKey.put(key, created);
        return new ObserveResult(true, created);
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

    public Set<String> finishScan(UUID playerId, Instant now, PlayerIncidentTracker incidents) {
        Map<String, FindingState> byKey = findings.get(playerId);
        if (byKey == null) {
            return Set.of();
        }
        Set<String> resolved = new HashSet<>();
        Iterator<Map.Entry<String, FindingState>> iterator = byKey.entrySet().iterator();
        while (iterator.hasNext()) {
            FindingState state = iterator.next().getValue();
            if (state.active && !state.seenThisScan) {
                state.active = false;
                state.resolvedAt = now;
                resolved.add(state.key);
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
        return resolved;
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

    private static IncidentType incidentType(ScanFinding finding) {
        return finding.classification() == ScanClassification.INVALID
                ? IncidentType.ILLEGAL_ITEM
                : IncidentType.SUSPICIOUS_ITEM;
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

        private FindingState(String key, SignalType type, Instant now, int slot, IncidentType unused) {
            this.key = key;
            this.type = type;
            this.firstSeen = now;
            this.lastSeen = now;
            this.slot = slot;
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
    }
}
