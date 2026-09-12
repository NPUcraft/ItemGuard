package com.npucraft.itemguard.trace;

import com.npucraft.itemguard.flow.model.ItemFlowEvent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded in-memory timeline. Oldest events are dropped when {@code maxEvents} is exceeded.
 */
public final class TraceBuffer {

    private final ArrayDeque<ItemFlowEvent> events = new ArrayDeque<>();
    private final int maxEvents;

    public TraceBuffer(int maxEvents) {
        this.maxEvents = Math.max(1, maxEvents);
    }

    public void add(ItemFlowEvent event) {
        events.addLast(event);
        while (events.size() > maxEvents) {
            events.removeFirst();
        }
    }

    public void purgeExpired(Instant cutoff) {
        while (!events.isEmpty() && events.peekFirst().timestamp().isBefore(cutoff)) {
            events.removeFirst();
        }
    }

    public List<ItemFlowEvent> query(Instant from, Instant to, String material) {
        List<ItemFlowEvent> matches = new ArrayList<>();
        for (ItemFlowEvent event : events) {
            if (event.timestamp().isBefore(from) || event.timestamp().isAfter(to)) {
                continue;
            }
            if (material != null && !event.material().equalsIgnoreCase(material)) {
                continue;
            }
            matches.add(event);
        }
        return List.copyOf(matches);
    }

    public int size() {
        return events.size();
    }

    public List<ItemFlowEvent> recent(int limit) {
        List<ItemFlowEvent> snapshot = new ArrayList<>(events);
        int from = Math.max(0, snapshot.size() - limit);
        return List.copyOf(snapshot.subList(from, snapshot.size()));
    }

    public static String normalizeMaterial(String material) {
        return material == null ? null : material.trim().toUpperCase(Locale.ROOT);
    }
}
