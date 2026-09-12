package com.npucraft.itemguard.log;

/**
 * Log-purpose categories. Detection still uses {@link com.npucraft.itemguard.risk.model.SignalType}.
 */
public enum ForensicLogType {
    ALERT,
    UNKNOWN_GAIN,
    INVALID_ITEM,
    SUSPICIOUS_ITEM,
    HUSKSYNC_DATA_APPLY,
    HIGH_VALUE_FLOW,
    ADMIN_ACTION
}
