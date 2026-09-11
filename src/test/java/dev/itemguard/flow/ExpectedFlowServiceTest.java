package dev.itemguard.flow;

import dev.itemguard.config.PluginSettings;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.SourceConfidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExpectedFlowServiceTest {

    @Test
    void pluginCreditKeepsCallerProviderAndIsNumeric() {
        ExpectedFlowService service = new ExpectedFlowService(new ExpectedFlowLedger(), PluginSettings.defaults());
        UUID player = UUID.randomUUID();
        service.creditPluginGain(player, "DIAMOND", 64, "itemguard-test", "txn-1");
        ExpectedFlowCredit credit = service.ledger().snapshot(player, Instant.now()).getFirst();
        assertEquals("itemguard-test", credit.provider());
        assertEquals(FlowSource.PLUGIN, credit.source());
        assertEquals(SourceConfidence.TRUSTED, credit.confidence());
        assertEquals(64, credit.remainingAmount());
        assertFalse(credit.oneShot());
    }
}
