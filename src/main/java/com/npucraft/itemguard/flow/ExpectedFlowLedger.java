package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.AttributionQuery;
import com.npucraft.itemguard.flow.model.ContainerIdentity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived expected inventory credits.
 * Numeric credits are exact (pickup / furnace extract) and support partial consume.
 * One-shot credits are source hints: tick-scale TTL, consumed once, never FIFO-leaked into later gains.
 */
public final class ExpectedFlowLedger {

    private final Map<UUID, List<ExpectedFlowCredit>> creditsByPlayer = new HashMap<>();

    public void add(ExpectedFlowCredit credit) {
        creditsByPlayer.computeIfAbsent(credit.playerId(), unused -> new ArrayList<>()).add(credit);
    }

    public List<Consumption> consume(UUID playerId, String material, int amount, Instant now) {
        return consume(playerId, material, amount, now, AttributionQuery.none());
    }

    public List<Consumption> consume(UUID playerId, String material, int amount, Instant now, AttributionQuery query) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null || amount <= 0) {
            return List.of();
        }
        List<Consumption> consumed = new ArrayList<>();
        int remaining = amount;
        remaining = consumeExactPass(credits, material, remaining, now, consumed);
        remaining = consumeHintPass(credits, material, remaining, now, query, consumed);
        compact(playerId, now);
        return List.copyOf(consumed);
    }

    private int consumeExactPass(
            List<ExpectedFlowCredit> credits,
            String material,
            int remaining,
            Instant now,
            List<Consumption> consumed
    ) {
        for (int index = 0; index < credits.size() && remaining > 0; index++) {
            ExpectedFlowCredit credit = credits.get(index);
            if (credit.oneShot() || credit.isExpired(now) || credit.remainingAmount() <= 0 || !credit.matches(material)) {
                continue;
            }
            int take = Math.min(remaining, credit.remainingAmount());
            credits.set(index, credit.consume(take));
            consumed.add(new Consumption(credit, take));
            remaining -= take;
        }
        return remaining;
    }

    private int consumeHintPass(
            List<ExpectedFlowCredit> credits,
            String material,
            int remaining,
            Instant now,
            AttributionQuery query,
            List<Consumption> consumed
    ) {
        while (remaining > 0) {
            int best = bestHintIndex(credits, material, now, query);
            if (best < 0) {
                break;
            }
            ExpectedFlowCredit credit = credits.get(best);
            int take = Math.min(remaining, credit.remainingAmount());
            credits.set(best, credit.consume(credit.remainingAmount()));
            consumed.add(new Consumption(credit, take));
            remaining -= take;
        }
        return remaining;
    }

    private int bestHintIndex(List<ExpectedFlowCredit> credits, String material, Instant now, AttributionQuery query) {
        int best = -1;
        int bestRank = Integer.MIN_VALUE;
        Instant bestCreated = Instant.EPOCH;
        for (int index = 0; index < credits.size(); index++) {
            ExpectedFlowCredit credit = credits.get(index);
            if (!credit.oneShot() || credit.isExpired(now) || credit.remainingAmount() <= 0 || !credit.matches(material)) {
                continue;
            }
            int rank = hintRank(credit, material, query);
            if (best < 0 || rank > bestRank || (rank == bestRank && credit.createdAt().isAfter(bestCreated))) {
                best = index;
                bestRank = rank;
                bestCreated = credit.createdAt();
            }
        }
        return best;
    }

    private static int hintRank(ExpectedFlowCredit credit, String material, AttributionQuery query) {
        int rank = 0;
        if (material.equals(credit.material())) {
            rank += 100;
        }
        if (query != null && query.preferredInteraction() != null && query.preferredInteraction().equals(credit.correlationId())) {
            rank += 40;
        }
        if (query != null && sameContainer(query.preferredContainer(), credit.container())) {
            rank += 20;
        }
        if (query != null && query.preferredSource() != null && query.preferredSource() == credit.source()) {
            rank += 10;
        }
        return rank;
    }

    private static boolean sameContainer(ContainerIdentity left, ContainerIdentity right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    public boolean hasMatchingHint(UUID playerId, String material, Instant now) {
        List<ExpectedFlowCredit> credits = creditsByPlayer.get(playerId);
        if (credits == null) {
            return false;
        }
        for (ExpectedFlowCredit credit : credits) {
            if (credit.oneShot() && !credit.isExpired(now) && credit.remainingAmount() > 0 && credit.matches(material)) {
                return true;
            }
        }
        return false;
    }

    public int hintCount(UUID playerId, Instant now) {
        return (int) snapshot(playerId, now).stream().filter(ExpectedFlowCredit::oneShot).count();
    }

    public int exactCount(UUID playerId, Instant now) {
        return (int) snapshot(playerId, now).stream().filter(credit -> !credit.oneShot()).count();
    }

    public ExpectedFlowCredit nearestHint(UUID playerId, Instant now) {
        ExpectedFlowCredit nearest = null;
        for (ExpectedFlowCredit credit : snapshot(playerId, now)) {
            if (!credit.oneShot()) {
                continue;
            }
            if (nearest == null || credit.createdAt().isAfter(nearest.createdAt())) {
                nearest = credit;
            }
        }
        return nearest;
    }

    /**
     * Snapshot of still-live credits. Call this before {@link #consume} so UNKNOWN diagnosis is not sampled
     * after hints have already been removed.
     */
    public Probe probe(UUID playerId, Instant now) {
        List<ExpectedFlowCredit> live = snapshot(playerId, now);
        return new Probe(live, nearestHint(playerId, now), hintCount(playerId, now), exactCount(playerId, now));
    }

    public record Probe(
            List<ExpectedFlowCredit> live,
            ExpectedFlowCredit nearestHint,
            int hintCount,
            int exactCount
    ) {
        public Probe {
            live = live == null ? List.of() : List.copyOf(live);
        }

        public ExpectedFlowCredit nearestMatching(String material) {
            ExpectedFlowCredit best = null;
            for (ExpectedFlowCredit credit : live) {
                if (!credit.oneShot() || !credit.matches(material)) {
                    continue;
                }
                if (best == null || credit.createdAt().isAfter(best.createdAt())) {
                    best = credit;
                }
            }
            return best;
        }

        public int matchingHintCount(String material) {
            int count = 0;
            for (ExpectedFlowCredit credit : live) {
                if (credit.oneShot() && credit.matches(material)) {
                    count++;
                }
            }
            return count;
        }
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
