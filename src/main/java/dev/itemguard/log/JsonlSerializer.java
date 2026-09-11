package dev.itemguard.log;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compact JSON object writer with correct escaping. No third-party JSON library.
 */
public final class JsonlSerializer {

    private JsonlSerializer() {
    }

    public static String toJson(ForensicLogRecord record, java.time.ZoneId zone) {
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        boolean first = true;
        first = number(json, first, "schemaVersion", record.schemaVersion());
        first = string(json, first, "timestamp", record.instant().atZone(zone)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")));
        first = number(json, first, "epochMillis", record.epochMillis());
        first = string(json, first, "type", record.type().name());
        first = string(json, first, "priority", record.priority().name());
        first = string(json, first, "serverName", record.serverName());
        first = string(json, first, "recordId", stringOf(record.recordId()));
        first = string(json, first, "playerUuid", stringOf(record.playerUuid()));
        first = string(json, first, "playerName", record.playerName());
        first = string(json, first, "material", record.material());
        first = number(json, first, "amount", record.amount());
        first = string(json, first, "source", record.source());
        first = string(json, first, "destination", record.destination());
        first = string(json, first, "sourceConfidence", record.sourceConfidence());
        first = number(json, first, "riskScore", record.riskScore());
        first = string(json, first, "riskLevel", record.riskLevel());
        first = strings(json, first, "signalTypes", record.signalTypes());
        first = strings(json, first, "findingTypes", record.findingTypes());
        first = string(json, first, "classification", record.classification());
        first = string(json, first, "worldUuid", stringOf(record.worldUuid()));
        first = string(json, first, "worldName", record.worldName());
        first = number(json, first, "x", record.x());
        first = number(json, first, "y", record.y());
        first = number(json, first, "z", record.z());
        first = string(json, first, "correlationId", stringOf(record.correlationId()));
        first = string(json, first, "itemSignature", detailedSignature(record.itemSignature()));
        first = string(json, first, "provider", record.provider());
        first = string(json, first, "summary", record.summary());
        map(json, first, "metadata", record.metadata());
        json.append('}');
        return json.toString();
    }

    private static String detailedSignature(String hash) {
        if (hash == null || hash.isBlank() || "material-only".equals(hash) || hash.startsWith("fallback:")) {
            return null;
        }
        return hash;
    }

    private static String stringOf(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static boolean string(StringBuilder json, boolean first, String key, String value) {
        if (value == null || value.isBlank()) {
            return first;
        }
        comma(json, first);
        quote(json, key);
        json.append(':');
        quote(json, value);
        return false;
    }

    private static boolean number(StringBuilder json, boolean first, String key, Number value) {
        if (value == null) {
            return first;
        }
        comma(json, first);
        quote(json, key);
        json.append(':').append(value);
        return false;
    }

    private static boolean strings(StringBuilder json, boolean first, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return first;
        }
        comma(json, first);
        quote(json, key);
        json.append(":[");
        boolean itemFirst = true;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!itemFirst) {
                json.append(',');
            }
            quote(json, value);
            itemFirst = false;
        }
        json.append(']');
        return false;
    }

    private static void map(StringBuilder json, boolean first, String key, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        comma(json, first);
        quote(json, key);
        json.append(":{");
        boolean itemFirst = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || sensitive(entry.getKey())) {
                continue;
            }
            if (!itemFirst) {
                json.append(',');
            }
            quote(json, entry.getKey());
            json.append(':');
            quote(json, entry.getValue() == null ? "" : entry.getValue());
            itemFirst = false;
        }
        json.append('}');
    }

    private static boolean sensitive(String key) {
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("nbt")
                || lower.equals("ip")
                || lower.contains("serialized")
                || lower.contains("pdcvalue")
                || lower.contains("pdc-value");
    }

    private static void comma(StringBuilder json, boolean first) {
        if (!first) {
            json.append(',');
        }
    }

    static void quote(StringBuilder json, String raw) {
        json.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        json.append(String.format("\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                }
            }
        }
        json.append('"');
    }
}
