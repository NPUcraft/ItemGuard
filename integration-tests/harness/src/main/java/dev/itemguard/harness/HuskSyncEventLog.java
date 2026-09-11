package dev.itemguard.harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HuskSyncEventLog {

    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

    public void record(Map<String, Object> event) {
        events.add(Map.copyOf(event));
    }

    public Map<String, Object> dump() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("events", List.copyOf(events));
        return map;
    }

    public List<Map<String, Object>> snapshot() {
        return new ArrayList<>(events);
    }
}
