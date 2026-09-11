package dev.itemguard.flow.model;

/**
 * How confident ItemGuard is about a flow's origin.
 * Trusted sources explain inventory deltas; they never subtract risk from unrelated signals.
 */
public enum SourceConfidence {
    VERIFIED,
    TRUSTED,
    INFERRED,
    UNKNOWN
}
