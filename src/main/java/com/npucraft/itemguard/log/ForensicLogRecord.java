package com.npucraft.itemguard.log;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Detached forensic event. Never holds Bukkit objects or live sessions.
 */
public record ForensicLogRecord(
        UUID recordId,
        Instant instant,
        long epochMillis,
        ForensicLogType type,
        ForensicLogPriority priority,
        String serverName,
        UUID playerUuid,
        String playerName,
        String material,
        Integer amount,
        String source,
        String destination,
        String sourceConfidence,
        Integer riskScore,
        String riskLevel,
        List<String> signalTypes,
        List<String> findingTypes,
        String classification,
        UUID worldUuid,
        String worldName,
        Integer x,
        Integer y,
        Integer z,
        UUID correlationId,
        String itemSignature,
        String provider,
        String summary,
        Map<String, String> metadata,
        UUID incidentId,
        String incidentType,
        Integer incidentRisk,
        String incidentAlertTransition,
        int schemaVersion
) {
    public static final int SCHEMA_VERSION = 2;

    public ForensicLogRecord {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        signalTypes = signalTypes == null ? List.of() : List.copyOf(signalTypes);
        findingTypes = findingTypes == null ? List.of() : List.copyOf(findingTypes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
        epochMillis = instant.toEpochMilli();
    }

    public static Builder builder(ForensicLogType type, ForensicLogPriority priority) {
        return new Builder(type, priority);
    }

    public static final class Builder {
        private UUID recordId = UUID.randomUUID();
        private Instant instant = Instant.now();
        private final ForensicLogType type;
        private final ForensicLogPriority priority;
        private String serverName = "unknown";
        private UUID playerUuid;
        private String playerName;
        private String material;
        private Integer amount;
        private String source;
        private String destination;
        private String sourceConfidence;
        private Integer riskScore;
        private String riskLevel;
        private List<String> signalTypes = List.of();
        private List<String> findingTypes = List.of();
        private String classification;
        private UUID worldUuid;
        private String worldName;
        private Integer x;
        private Integer y;
        private Integer z;
        private UUID correlationId;
        private String itemSignature;
        private String provider;
        private String summary;
        private Map<String, String> metadata = Map.of();
        private UUID incidentId;
        private String incidentType;
        private Integer incidentRisk;
        private String incidentAlertTransition;

        private Builder(ForensicLogType type, ForensicLogPriority priority) {
            this.type = type;
            this.priority = priority;
        }

        public Builder recordId(UUID recordId) {
            this.recordId = recordId;
            return this;
        }

        public Builder instant(Instant instant) {
            this.instant = instant;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName == null || serverName.isBlank() ? "unknown" : serverName;
            return this;
        }

        public Builder player(UUID playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            return this;
        }

        public Builder material(String material) {
            this.material = material;
            return this;
        }

        public Builder amount(Integer amount) {
            this.amount = amount;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }

        public Builder sourceConfidence(String sourceConfidence) {
            this.sourceConfidence = sourceConfidence;
            return this;
        }

        public Builder risk(Integer riskScore, String riskLevel) {
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder signalTypes(List<String> signalTypes) {
            this.signalTypes = signalTypes;
            return this;
        }

        public Builder findingTypes(List<String> findingTypes) {
            this.findingTypes = findingTypes;
            return this;
        }

        public Builder classification(String classification) {
            this.classification = classification;
            return this;
        }

        public Builder location(UUID worldUuid, String worldName, Integer x, Integer y, Integer z) {
            this.worldUuid = worldUuid;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder itemSignature(String itemSignature) {
            this.itemSignature = itemSignature;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder incident(UUID incidentId, String incidentType, Integer incidentRisk, String incidentAlertTransition) {
            this.incidentId = incidentId;
            this.incidentType = incidentType;
            this.incidentRisk = incidentRisk;
            this.incidentAlertTransition = incidentAlertTransition;
            return this;
        }

        public ForensicLogRecord build() {
            return new ForensicLogRecord(
                    recordId,
                    instant,
                    instant.toEpochMilli(),
                    type,
                    priority,
                    serverName,
                    playerUuid,
                    playerName,
                    material,
                    amount,
                    source,
                    destination,
                    sourceConfidence,
                    riskScore,
                    riskLevel,
                    signalTypes,
                    findingTypes,
                    classification,
                    worldUuid,
                    worldName,
                    x,
                    y,
                    z,
                    correlationId,
                    itemSignature,
                    provider,
                    summary,
                    metadata,
                    incidentId,
                    incidentType,
                    incidentRisk,
                    incidentAlertTransition,
                    SCHEMA_VERSION
            );
        }
    }
}
