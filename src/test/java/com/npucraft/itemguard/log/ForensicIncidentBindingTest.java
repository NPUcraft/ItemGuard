package com.npucraft.itemguard.log;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.RiskEngine;
import com.npucraft.itemguard.risk.incident.IncidentType;
import com.npucraft.itemguard.risk.incident.RiskIncidentSnapshot;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ForensicIncidentBindingTest {

    @Test
    void eventIncidentIsMaterialOwnedNotPlayerHighest() {
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        RiskAssessment assessment = engine.assess(player, List.of(
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 35, now, "OMINOUS_TRIAL_KEY", 63),
                signal(player, SignalType.UNEXPLAINED_ITEM_GAIN, 25, now, "SHULKER_BOX", 1)
        ), now);
        assertEquals(35, assessment.score());
        RiskIncidentSnapshot keys = ForensicLogPublisher.eventIncident(assessment, "OMINOUS_TRIAL_KEY", IncidentType.ITEM_GAIN);
        RiskIncidentSnapshot shulker = ForensicLogPublisher.eventIncident(assessment, "SHULKER_BOX", IncidentType.ITEM_GAIN);
        assertNotNull(keys);
        assertNotNull(shulker);
        assertNotEquals(keys.incidentId(), shulker.incidentId());
        assertEquals(keys.incidentId(), assessment.highestIncidentId());
        assertNotEquals(shulker.incidentId(), assessment.highestIncidentId());
        assertEquals(25, shulker.score());
        Map<String, String> metadata = new LinkedHashMap<>();
        ForensicLogPublisher.putHighestIncident(metadata, assessment);
        assertEquals(assessment.highestIncidentId().toString(), metadata.get("playerHighestIncidentId"));
        assertEquals("35", metadata.get("playerCurrentRisk"));
    }

    private static RiskSignal signal(UUID player, SignalType type, int score, Instant now, String material, int amount) {
        return new RiskSignal(
                UUID.randomUUID(),
                player,
                type,
                score,
                Severity.SUSPICIOUS,
                now,
                now.plusSeconds(60),
                Map.of("source", "UNKNOWN"),
                material + " unexplained",
                material,
                ItemSignature.materialOnly(material),
                amount,
                UUID.randomUUID(),
                null
        );
    }
}
