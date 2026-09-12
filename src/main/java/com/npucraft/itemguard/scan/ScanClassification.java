package com.npucraft.itemguard.scan;

/**
 * Scanner classification. CUSTOM is not cheating; INVALID is a data-rule violation.
 */
public enum ScanClassification {
    INVALID,
    SUSPICIOUS,
    CUSTOM,
    INFO
}
