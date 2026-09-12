package com.npucraft.itemguard.risk.incident;

/**
 * Coarse incident families. Unrelated families never share a score bucket.
 */
public enum IncidentType {
    ITEM_GAIN,
    ILLEGAL_ITEM,
    SUSPICIOUS_ITEM,
    SHULKER_ACTIVITY
}
