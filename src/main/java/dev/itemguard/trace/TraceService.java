package dev.itemguard.trace;

import dev.itemguard.config.PluginSettings;
import dev.itemguard.flow.model.ItemFlowEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TraceService {

    private final Map<UUID, TraceBuffer> buffers = new ConcurrentHashMap<>();
    private volatile PluginSettings settings;
    private volatile int totalEvents;

    public TraceService(PluginSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void record(ItemFlowEvent event) {
        TraceBuffer buffer = buffers.computeIfAbsent(event.playerId(), unused -> new TraceBuffer(settings.maxTraceEventsPerPlayer()));
        buffer.add(event);
        totalEvents = Math.min(totalEvents + 1, settings.maxTotalTraceEvents());
        if (totalEvents >= settings.maxTotalTraceEvents()) {
            purgeExpired(Instant.now());
        }
    }

    public List<ItemFlowEvent> query(UUID playerId, Duration duration, String material) {
        TraceBuffer buffer = buffers.get(playerId);
        if (buffer == null) {
            return List.of();
        }
        Instant to = Instant.now();
        Instant from = to.minus(duration);
        return buffer.query(from, to, TraceBuffer.normalizeMaterial(material));
    }

    public List<ItemFlowEvent> recent(UUID playerId, int limit) {
        TraceBuffer buffer = buffers.get(playerId);
        return buffer == null ? List.of() : buffer.recent(limit);
    }

    public int playerCount(UUID playerId) {
        TraceBuffer buffer = buffers.get(playerId);
        return buffer == null ? 0 : buffer.size();
    }

    public int totalEvents() {
        int sum = 0;
        for (TraceBuffer buffer : buffers.values()) {
            sum += buffer.size();
        }
        totalEvents = sum;
        return sum;
    }

    public void purgeExpired(Instant now) {
        Instant cutoff = now.minus(settings.traceRetention());
        List<UUID> empty = new ArrayList<>();
        buffers.forEach((id, buffer) -> {
            buffer.purgeExpired(cutoff);
            if (buffer.size() == 0) {
                empty.add(id);
            }
        });
        empty.forEach(buffers::remove);
        totalEvents();
    }

    public void dropPlayer(UUID playerId, boolean keepTraces) {
        if (!keepTraces) {
            buffers.remove(playerId);
        }
    }

    public void clear() {
        buffers.clear();
        totalEvents = 0;
    }
}
