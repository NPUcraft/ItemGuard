package dev.itemguard.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived expected inventory credits. Numeric credits are exact (pickup).
 * One-shot credits explain an actual gain then discard leftover so they cannot leak into later events.
 */
public final class ExpectedFlowLedger {

    private final Map<UUID, List<ExpectedFlowCredit>> creditsByPlayer = new HashMap<>();

    public void add(ExpectedFlowCredit credit) {
        creditsByPlayer.computeIfAbsent(credit.playerId(), unused -> new ArrayList<>()).add(credit);
    }

    public List<Consumption> consume(UUID playerId, String material, int amount, Instant now) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null || amount <= 0) {
            return List.of();
        }
        List<Consumption> consumed = new ArrayList<>();
        int remaining = amount;
        remaining = consumePass(credits, material, remaining, now, false, consumed);
        remaining = consumePass(credits, material, remaining, now, true, consumed);
        compact(playerId, now);
        return List.copyOf(consumed);
    }

    private int consumePass(
            List<ExpectedFlowCredit> credits,
            String material,
            int remaining,
            Instant now,
            boolean oneShotPass,
            List<Consumption> consumed
    ) {
        for (int index = 0; index < credits.size() && remaining > 0; index++) {
            ExpectedFlowCredit credit = credits.get(index);
            if (credit.oneShot() != oneShotPass) {
                continue;
            }
            if (credit.isExpired(now) || credit.remainingAmount() <= 0 || !credit.matches(material)) {
                continue;
            }
            int take = Math.min(remaining, credit.remainingAmount());
            ExpectedFlowCredit updated = credit.oneShot()
                    ? credit.consume(credit.remainingAmount())
                    : credit.consume(take);
            credits.set(index, updated);
            consumed.add(new Consumption(credit, take));
            remaining -= take;
        }
        return remaining;
    }

    public void discardOneShot(UUID playerId) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null) {
            return;
        }
        credits.removeIf(ExpectedFlowCredit::oneShot);
        if (credits.isEmpty()) {
            creditsByPlayer.remove(playerId);
        }
    }

    public void purgeExpired(Instant now) {
        for (UUID playerId : List.copyOf(creditsByPlayer.keySet())) {
            compact(playerId, now);
        }
    }

    public List<ExpectedFlowCredit> snapshot(UUID playerId, Instant now) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null) {
            return List.of();
        }
        return credits.stream()
                .filter(credit -> !credit.isExpired(now) && credit.remainingAmount() > 0)
                .toList();
    }

    public void clear(UUID playerId) {
        creditsByPlayer.remove(playerId);
    }

    public int size(UUID playerId) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        return credits == null ? 0 : credits.size();
    }

    public int totalSize() {
        int total = 0;
        for (List<ExpectedFlowCredit> credits : creditsByPlayer.values()) {
            total += credits.size();
        }
        return total;
    }

    private void compact(UUID playerId, Instant now) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null) {
            return;
        }
        Iterator<ExpectedFlowCredit> iterator = credits.iterator();
        while (iterator.hasNext()) {
            ExpectedFlowCredit credit = iterator.next();
            if (credit.isExpired(now) || credit.remainingAmount() <= 0) {
                iterator.remove();
            }
        }
        if (credits.isEmpty()) {
            creditsByPlayer.remove(playerId);
        }
    }

    public record Consumption(ExpectedFlowCredit credit, int amount) {
    }
}
