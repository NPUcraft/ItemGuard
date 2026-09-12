package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RiskSignals {

    private RiskSignals() {
    }

    public static RiskSignal create(
            UUID playerId,
            SignalType type,
            RiskSettings settings,
            Severity severity,
            String material,
            ItemSignature signature,
            int amount,
            UUID correlationId,
            UUID flowEventId,
            String description,
            Map<String, String> evidence
    ) {
        return create(
                playerId,
                type,
                settings,
                severity,
                material,
                signature,
                amount,
                correlationId,
                flowEventId,
                description,
                evidence,
                settings.score(type),
                settings.signalTtl()
        );
    }

    public static RiskSignal create(
            UUID playerId,
            SignalType type,
            RiskSettings settings,
            Severity severity,
            String material,
            ItemSignature signature,
            int amount,
            UUID correlationId,
            UUID flowEventId,
            String description,
            Map<String, String> evidence,
            int score
    ) {
        return create(
                playerId,
                type,
                settings,
                severity,
                material,
                signature,
                amount,
                correlationId,
                flowEventId,
                description,
                evidence,
                score,
                settings.signalTtl()
        );
    }

    public static RiskSignal create(
            UUID playerId,
            SignalType type,
            RiskSettings settings,
            Severity severity,
            String material,
            ItemSignature signature,
            int amount,
            UUID correlationId,
            UUID flowEventId,
            String description,
            Map<String, String> evidence,
            int score,
            Duration ttl
    ) {
        Instant now = Instant.now();
        Map<String, String> data = new LinkedHashMap<>();
        if (evidence != null) {
            data.putAll(evidence);
        }
        data.putIfAbsent("type", type.name());
        if (material != null) {
            data.putIfAbsent("material", material);
        }
        data.putIfAbsent("amount", Integer.toString(amount));
        data.putIfAbsent("score", Integer.toString(Math.max(0, score)));
        return new RiskSignal(
                UUID.randomUUID(),
                playerId,
                type,
                Math.max(0, score),
                severity,
                now,
                now.plus(ttl == null ? settings.signalTtl() : ttl),
                data,
                description,
                material,
                signature,
                amount,
                correlationId,
                flowEventId
        );
    }
}
