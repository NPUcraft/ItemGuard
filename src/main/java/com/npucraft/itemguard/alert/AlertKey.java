package com.npucraft.itemguard.alert;

import java.util.Objects;
import java.util.UUID;

public record AlertKey(UUID playerId, String signalType, String material) {
    public AlertKey {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(signalType, "signalType");
        material = material == null ? "*" : material;
    }
}
