package dev.itemguard.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matches actual inventory gains against expected credits. Remainder is unexplained gain.
 */
public final class UnknownGainCorrelator {

    public CorrelationResult correlate(UUID playerId, Map<String, Integer> actualGains, ExpectedFlowLedger ledger, Instant now) {
        List<ExpectedFlowLedger.Consumption> explained = new ArrayList<>();
        List<UnexplainedGain> unexplained = new ArrayList<>();
        actualGains.forEach((material, amount) -> {
            List<ExpectedFlowLedger.Consumption> consumed = ledger.consume(playerId, material, amount, now);
            int explainedAmount = consumed.stream().mapToInt(ExpectedFlowLedger.Consumption::amount).sum();
            explained.addAll(consumed);
            int leftover = amount - explainedAmount;
            if (leftover > 0) {
                unexplained.add(new UnexplainedGain(material, leftover));
            }
        });
        return new CorrelationResult(List.copyOf(explained), List.copyOf(unexplained));
    }

    public record UnexplainedGain(String material, int amount) {
    }

    public record CorrelationResult(
            List<ExpectedFlowLedger.Consumption> explained,
            List<UnexplainedGain> unexplained
    ) {
        public int unexplainedAmount() {
            return unexplained.stream().mapToInt(UnexplainedGain::amount).sum();
        }
    }
}
