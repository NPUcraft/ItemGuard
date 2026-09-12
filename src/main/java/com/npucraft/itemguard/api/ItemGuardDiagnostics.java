package com.npucraft.itemguard.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only ItemGuard view for staff tools and integration tests.
 * Implementations must not expose mutable session objects or live inventories.
 */
@Experimental
public interface ItemGuardDiagnostics {

    PlayerDiagnosticSnapshot player(UUID playerId);

    PlayerDiagnosticSnapshot player(String name);

    List<ScanFindingView> scan(UUID playerId);

    boolean huskSyncDetected();

    boolean huskSyncActive();

    /**
     * Read-only snapshot of values currently loaded by {@code ConfigManager}.
     * Missing keys are in-memory defaults; this must never rewrite operator YAML.
     */
    default Map<String, Object> runtimeSettings() {
        return Map.of();
    }
}
