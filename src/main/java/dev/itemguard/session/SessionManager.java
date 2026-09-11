package dev.itemguard.session;

import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final Map<UUID, PlayerGuardSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> recentNames = new ConcurrentHashMap<>();

    public PlayerGuardSession create(Player player, Instant now) {
        PlayerGuardSession session = new PlayerGuardSession(player, now);
        sessions.put(player.getUniqueId(), session);
        recentNames.put(player.getName().toLowerCase(), player.getUniqueId());
        return session;
    }

    public PlayerGuardSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public PlayerGuardSession require(Player player, Instant now) {
        recentNames.put(player.getName().toLowerCase(), player.getUniqueId());
        return sessions.computeIfAbsent(player.getUniqueId(), unused -> new PlayerGuardSession(player, now));
    }

    public UUID lookup(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        org.bukkit.entity.Player exact = org.bukkit.Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact.getUniqueId();
        }
        for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online.getUniqueId();
            }
        }
        return recentNames.get(name.toLowerCase());
    }

    public String nameOf(UUID playerId) {
        PlayerGuardSession session = sessions.get(playerId);
        if (session != null) {
            return session.playerName();
        }
        for (Map.Entry<String, UUID> entry : recentNames.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                return entry.getKey();
            }
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(playerId);
        return online == null ? playerId.toString() : online.getName();
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public Collection<PlayerGuardSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
