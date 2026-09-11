package dev.itemguard.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDiagnosticSnapshotTest {

    @Test
    void copiesMapsSoCallersCannotMutateSessionState() {
        Map<String, Integer> totals = new HashMap<>();
        totals.put("DIAMOND", 1);
        PlayerDiagnosticSnapshot snapshot = new PlayerDiagnosticSnapshot(
                UUID.randomUUID(),
                "ItemGuardBot",
                true,
                true,
                false,
                false,
                1L,
                2L,
                0,
                "NORMAL",
                "IDLE",
                null,
                totals,
                Map.of(),
                Map.of("DIAMOND", 1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0L,
                0L,
                0L,
                null,
                0,
                false,
                false
        );
        totals.put("DIAMOND", 64);
        assertEquals(1, snapshot.materialTotals().get("DIAMOND"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.materialTotals().put("STONE", 1));
    }
}
