package com.npucraft.itemguard.risk.incident;

import java.util.Locale;
import java.util.Objects;

public record IncidentKey(IncidentType type, String discriminator) {
    public IncidentKey {
        Objects.requireNonNull(type, "type");
        discriminator = discriminator == null || discriminator.isBlank()
                ? "*"
                : discriminator.toUpperCase(Locale.ROOT);
    }

    public static IncidentKey itemGain(String material) {
        return new IncidentKey(IncidentType.ITEM_GAIN, material == null || material.isBlank() ? "*" : material);
    }

    public static IncidentKey shulker() {
        return new IncidentKey(IncidentType.SHULKER_ACTIVITY, "SHULKER");
    }

    public static IncidentKey scanner(IncidentType type, String signaturePart, String findingType) {
        return new IncidentKey(type, (signaturePart == null ? "*" : signaturePart) + "|" + findingType);
    }
}
