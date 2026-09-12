package com.npucraft.itemguard.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sliding window of confirmed incoming item flows used by burst / repeated-item detectors.
 */
public final class ItemGainWindow {

    private final ArrayDeque<Gain> gains = new ArrayDeque<>();

    public void record(Instant timestamp, String material, int amount, int valueWeight, boolean unexplained, String signatureHash, String source) {
        gains.addLast(new Gain(timestamp, material, amount, valueWeight, unexplained, signatureHash, source));
    }

    public void purge(Instant now, Duration maxWindow) {
        Instant cutoff = now.minus(maxWindow);
        while (!gains.isEmpty() && gains.peekFirst().timestamp().isBefore(cutoff)) {
            gains.removeFirst();
        }
    }

    public List<Gain> inWindow(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        List<Gain> matches = new ArrayList<>();
        for (Gain gain : gains) {
            if (!gain.timestamp().isBefore(cutoff)) {
                matches.add(gain);
            }
        }
        return List.copyOf(matches);
    }

    public int totalAmount(Instant now, Duration window) {
        return inWindow(now, window).stream().mapToInt(Gain::amount).sum();
    }

    public int unexplainedAmount(Instant now, Duration window) {
        return inWindow(now, window).stream().filter(Gain::unexplained).mapToInt(Gain::amount).sum();
    }

    public int totalValue(Instant now, Duration window) {
        return inWindow(now, window).stream().mapToInt(gain -> gain.amount() * gain.valueWeight()).sum();
    }

    public int unexplainedValue(Instant now, Duration window) {
        return inWindow(now, window).stream()
                .filter(Gain::unexplained)
                .mapToInt(gain -> gain.amount() * gain.valueWeight())
                .sum();
    }

    public Map<String, Integer> signatureTotals(Instant now, Duration window) {
        return signatureTotals(now, window, false);
    }

    public Map<String, Integer> unexplainedSignatureTotals(Instant now, Duration window) {
        return signatureTotals(now, window, true);
    }

    private Map<String, Integer> signatureTotals(Instant now, Duration window, boolean unexplainedOnly) {
        Map<String, Integer> totals = new HashMap<>();
        for (Gain gain : inWindow(now, window)) {
            if (unexplainedOnly && !gain.unexplained()) {
                continue;
            }
            if (gain.signatureHash() == null || gain.signatureHash().isBlank()) {
                continue;
            }
            totals.merge(gain.signatureHash(), gain.amount(), Integer::sum);
        }
        return Map.copyOf(totals);
    }

    public List<Gain> recent(int limit) {
        List<Gain> snapshot = new ArrayList<>(gains);
        int from = Math.max(0, snapshot.size() - limit);
        return List.copyOf(snapshot.subList(from, snapshot.size()));
    }

    public record Gain(
            Instant timestamp,
            String material,
            int amount,
            int valueWeight,
            boolean unexplained,
            String signatureHash,
            String source
    ) {
    }
}
