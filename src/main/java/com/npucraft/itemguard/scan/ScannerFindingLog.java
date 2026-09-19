package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Instant;
import java.util.UUID;

/**
 * Bounded scanner observation for forensic logs. Never carries NBT, PDC values, or full inventories.
 */
public record ScannerFindingLog(
        Instant timestamp,
        UUID playerId,
        UUID correlationId,
        String findingId,
        FindingLifecycle lifecycle,
        String ruleId,
        String triggerReason,
        String material,
        int amount,
        SignalType signalType,
        ScanClassification classification,
        Severity severity,
        int riskApplied,
        ItemSignature signature
) {
    public static String boundedReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String oneLine = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return oneLine.length() <= 96 ? oneLine : oneLine.substring(0, 96);
    }
}
