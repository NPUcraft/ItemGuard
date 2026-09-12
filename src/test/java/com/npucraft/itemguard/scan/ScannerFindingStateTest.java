package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.RiskEngine;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerFindingStateTest {

    @Test
    void sameSignatureFindingDoesNotAccumulate() {
        ScannerFindingObserver observer = observer();
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ScanFinding finding = conflicting(sig("boots-abc"));
        int produced = 0;
        for (int i = 0; i < 50; i++) {
            List<RiskSignal> signals = observer.observe(player, List.of(finding), UUID.randomUUID(), now.plusMillis(i), engine.incidents());
            produced += signals.size();
            engine.assess(player, signals, now.plusMillis(i));
        }
        assertEquals(1, produced);
        assertEquals(1, engine.incidents().active(player, now.plusSeconds(1)).size());
        assertEquals(RiskSettings.defaults().score(SignalType.CONFLICTING_ENCHANTMENTS), engine.incidents().currentRisk(player, now.plusSeconds(1)));
    }

    @Test
    void sameSignatureRepeatedScanRefreshesLastSeen() {
        ScannerFindingTracker tracker = new ScannerFindingTracker();
        UUID player = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-09-12T00:00:00Z");
        Instant t2 = t1.plusSeconds(5);
        ScanFinding finding = conflicting(sig("boots-abc"));
        tracker.beginScan(player);
        assertTrue(tracker.observe(player, finding, 1, t1).newRiskEvidence());
        tracker.finishScan(player, t1, null);
        tracker.beginScan(player);
        ScannerFindingTracker.ObserveResult second = tracker.observe(player, finding, 2, t2);
        tracker.finishScan(player, t2, null);
        assertTrue(!second.newRiskEvidence());
        assertEquals(2, second.state().slot());
        assertEquals(t2, second.state().lastSeen());
    }

    @Test
    void sameItemMovedSlotStillSameFinding() {
        ScannerFindingObserver observer = observer();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ScanFinding finding = conflicting(sig("boots-abc"));
        assertEquals(1, observer.observe(player, List.of(finding), UUID.randomUUID(), now, null).size());
        assertEquals(0, observer.observe(player, List.of(finding), UUID.randomUUID(), now.plusMillis(20), null).size());
    }

    @Test
    void findingResolvedWhenItemGone() {
        ScannerFindingObserver observer = observer();
        RiskEngine engine = new RiskEngine(RiskSettings.defaults());
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        ScanFinding finding = overLevel(sig("sword-abc"));
        engine.assess(player, observer.observe(player, List.of(finding), UUID.randomUUID(), now, engine.incidents()), now);
        assertEquals(1, engine.incidents().active(player, now).size());
        observer.observe(player, List.of(), UUID.randomUUID(), now.plusSeconds(1), engine.incidents());
        engine.assess(player, List.of(), now.plusSeconds(1));
        assertTrue(engine.incidents().active(player, now.plusSeconds(1)).isEmpty());
        assertEquals(0, engine.incidents().currentRisk(player, now.plusSeconds(1)));
    }

    @Test
    void sameSignatureDifferentPlayerCreatesSeparateFinding() {
        ScannerFindingObserver observer = observer();
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        Instant now = Instant.now();
        ScanFinding finding = conflicting(sig("shared"));
        assertEquals(1, observer.observe(steve, List.of(finding), UUID.randomUUID(), now, null).size());
        assertEquals(1, observer.observe(alex, List.of(finding), UUID.randomUUID(), now, null).size());
    }

    @Test
    void customMetadataZeroRisk() {
        ScannerFindingObserver observer = observer();
        UUID player = UUID.randomUUID();
        ScanFinding custom = new ScanFinding(
                "custom-metadata",
                SignalType.CUSTOM_ITEM_METADATA,
                ScanClassification.CUSTOM,
                Severity.CUSTOM,
                "DIAMOND_SWORD",
                sig("custom"),
                1,
                "Custom name",
                Map.of("kind", "custom-name")
        );
        assertTrue(observer.observe(player, List.of(custom), UUID.randomUUID(), Instant.now(), null).isEmpty());
    }

    @Test
    void componentModifiedZeroRisk() {
        ScannerFindingObserver observer = observer();
        UUID player = UUID.randomUUID();
        ScanFinding modified = new ScanFinding(
                "components",
                SignalType.COMPONENT_MODIFIED,
                ScanClassification.INFO,
                Severity.INFO,
                "DIAMOND_SWORD",
                sig("dmg"),
                1,
                "Modified component DAMAGE",
                Map.of("component", "DAMAGE")
        );
        assertTrue(observer.observe(player, List.of(modified), UUID.randomUUID(), Instant.now(), null).isEmpty());
    }

    private static ScannerFindingObserver observer() {
        return new ScannerFindingObserver(RiskSettings.defaults());
    }

    private static ItemSignature sig(String hash) {
        return new ItemSignature("NETHERITE_BOOTS", hash);
    }

    private static ScanFinding conflicting(ItemSignature signature) {
        return new ScanFinding(
                "enchantments",
                SignalType.CONFLICTING_ENCHANTMENTS,
                ScanClassification.SUSPICIOUS,
                Severity.SUSPICIOUS,
                "NETHERITE_BOOTS",
                signature,
                1,
                "protection conflicts with blast_protection",
                Map.of("left", "protection", "right", "blast_protection")
        );
    }

    private static ScanFinding overLevel(ItemSignature signature) {
        return new ScanFinding(
                "enchantments",
                SignalType.OVER_LEVEL_ENCHANTMENT,
                ScanClassification.INVALID,
                Severity.INVALID,
                "DIAMOND_SWORD",
                signature,
                1,
                "sharpness 255 exceeds max 5",
                Map.of("enchantment", "minecraft:sharpness", "level", "255")
        );
    }
}
