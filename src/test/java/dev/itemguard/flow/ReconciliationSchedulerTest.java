package dev.itemguard.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationSchedulerTest {

    @Test
    void heartbeatIntervalIsGameTicksNotPeriodicCycles() {
        assertFalse(ReconciliationScheduler.isHeartbeatDue(1, 20, 40));
        assertTrue(ReconciliationScheduler.isHeartbeatDue(2, 20, 40));
        assertFalse(ReconciliationScheduler.isHeartbeatDue(3, 20, 40));
        assertTrue(ReconciliationScheduler.isHeartbeatDue(4, 20, 40));
    }

    @Test
    void disabledHeartbeatNeverFires() {
        assertFalse(ReconciliationScheduler.isHeartbeatDue(2, 20, 0));
        assertFalse(ReconciliationScheduler.isHeartbeatDue(2, 20, -1));
    }
}
