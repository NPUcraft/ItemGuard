package com.npucraft.itemguard.risk;

import com.npucraft.itemguard.risk.model.SignalType;

import java.util.Set;

public final class SignalFamilies {

    public static final String CUSTOM_METADATA = "CUSTOM_METADATA";

    private static final Set<SignalType> CUSTOM_METADATA_TYPES = Set.of(
            SignalType.CUSTOM_ITEM_METADATA,
            SignalType.COMPONENT_MODIFIED,
            SignalType.CUSTOM_MAX_STACK,
            SignalType.SUSPICIOUS_COMPONENT
    );

    private SignalFamilies() {
    }

    public static String family(SignalType type) {
        if (CUSTOM_METADATA_TYPES.contains(type)) {
            return CUSTOM_METADATA;
        }
        return type.name();
    }

    public static boolean isCustomMetadata(SignalType type) {
        return CUSTOM_METADATA_TYPES.contains(type);
    }
}
