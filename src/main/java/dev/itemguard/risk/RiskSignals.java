package dev.itemguard.risk;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.item.ItemSignature;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;

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
        data.putIfAbsent("score", Integer.toString(settings.score(type)));
        return new RiskSignal(
                UUID.randomUUID(),
                playerId,
                type,
                settings.score(type),
                severity,
                now,
                now.plus(settings.signalTtl()),
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
