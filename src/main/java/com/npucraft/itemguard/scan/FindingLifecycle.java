package com.npucraft.itemguard.scan;

/**
 * Scanner finding observation relative to the player's current scan.
 * REFRESH/RESOLVED must not create a new risk contribution.
 */
public enum FindingLifecycle {
    NEW,
    REFRESH,
    RESOLVED
}
