package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.AttributionQuery;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.LocationRef;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import com.npucraft.itemguard.risk.RiskEngine;
import com.npucraft.itemguard.risk.detector.BurstDetector;
import com.npucraft.itemguard.risk.detector.RiskDetector;
import com.npucraft.itemguard.risk.model.RiskAssessment;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.scan.IllegalItemScanner;
import com.npucraft.itemguard.session.PlayerGuardSession;
import com.npucraft.itemguard.session.SessionManager;
import com.npucraft.itemguard.trace.TraceService;
import com.npucraft.itemguard.util.PerformanceStats;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event attribution + inventory reconciliation. Expected credits explain deltas; remainder is UNKNOWN.
 */
public final class ReconciliationService {

    private final SessionManager sessions;
    private final InventorySnapshotService snapshots;
    private final ExpectedFlowService expectedFlows;
    private final UnknownGainCorrelator correlator;
    private final TraceService traces;
    private final RiskEngine riskEngine;
    private final List<RiskDetector> detectors;
    private final BurstDetector burstDetector;
    private final IllegalItemScanner scanner;
    private final Logger logger;
    private final Consumer<ReconciliationResult> resultConsumer;
    private final PerformanceStats performance;
    private volatile PluginSettings settings;
    private volatile RiskSettings riskSettings;
    private volatile Consumer<Player> followUp;

    public ReconciliationService(
            SessionManager sessions,
            InventorySnapshotService snapshots,
            ExpectedFlowService expectedFlows,
            UnknownGainCorrelator correlator,
            TraceService traces,
            RiskEngine riskEngine,
            List<RiskDetector> detectors,
            BurstDetector burstDetector,
            IllegalItemScanner scanner,
            Logger logger,
            Consumer<ReconciliationResult> resultConsumer,
            PerformanceStats performance,
            PluginSettings settings,
            RiskSettings riskSettings
    ) {
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.expectedFlows = expectedFlows;
        this.correlator = correlator;
        this.traces = traces;
        this.riskEngine = riskEngine;
        this.detectors = detectors;
        this.burstDetector = burstDetector;
        this.scanner = scanner;
        this.logger = logger;
        this.resultConsumer = resultConsumer;
        this.performance = performance;
        this.settings = settings;
        this.riskSettings = riskSettings;
    }

    public void setFollowUp(Consumer<Player> followUp) {
        this.followUp = followUp;
    }

    public void updateSettings(PluginSettings settings, RiskSettings riskSettings) {
        this.settings = settings;
        this.riskSettings = riskSettings;
    }

    public PerformanceStats performance() {
        return performance;
    }

    public void establishBaseline(Player player) {
        PlayerGuardSession session = sessions.require(player, Instant.now());
        InventorySnapshot snapshot = snapshots.capture(player, Instant.now());
        session.setLastSnapshot(snapshot);
        session.setLastReconcileTime(snapshot.timestamp());
        session.clearDirty();
        expectedFlows.ledger().clear(player.getUniqueId());
        session.setAttributionGraceUsed(false);
        session.setLastAttribution(null);
    }

    public ReconciliationResult reconcile(Player player, String reason) {
        Instant now = Instant.now();
        PlayerGuardSession session = sessions.require(player, now);
        session.updateName(player.getName());
        long started = System.nanoTime();
        try {
            if (session.huskSync().isSyncing() && !"husksync-complete".equals(reason)) {
                session.clearDirty();
                return ReconciliationResult.skipped(session.playerId(), "husksync-syncing");
            }
            InventorySnapshot current = snapshots.capture(player, now);
            InventorySnapshot previous = session.lastSnapshot();
            if (previous == null) {
                session.setLastSnapshot(current);
                session.setLastReconcileTime(now);
                session.clearDirty();
                expectedFlows.ledger().clear(player.getUniqueId());
                return ReconciliationResult.baseline(session.playerId());
            }

            Map<String, Integer> delta = MaterialDiff.subtract(current.materialTotals(), previous.materialTotals());
            session.setLastDiff(delta);
            Map<String, Integer> gains = InventoryEconomics.flowGains(previous, current);
            UUID correlationId = UUID.randomUUID();
            LocationRef playerLocation = LocationRef.unknown();
            if (player.getWorld() != null) {
                playerLocation = new LocationRef(
                        player.getWorld().getUID(),
                        player.getWorld().getName(),
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockY(),
                        player.getLocation().getBlockZ()
                );
            }

            List<ItemFlowEvent> flows = new ArrayList<>();
            AttributionDecision attribution = null;
            if ("husksync-complete".equals(reason)) {
                expectedFlows.ledger().clear(player.getUniqueId());
                session.setAttributionGraceUsed(false);
                flows.addAll(recordAttributedGains(
                        player,
                        now,
                        gains,
                        FlowSource.HUSKSYNC_DATA_APPLY,
                        FlowDestination.PLAYER_INVENTORY,
                        SourceConfidence.TRUSTED,
                        playerLocation,
                        "HuskSync data apply",
                        correlationId
                ));
            } else {
                expectedFlows.ledger().purgeExpired(now);
                if (gains.isEmpty() && session.discardHintsAfterReconcile() && expectedFlows.ledger().hintCount(player.getUniqueId(), now) > 0) {
                    session.setDiscardHintsAfterReconcile(false);
                    session.clearDirty();
                    requestFollowUp(player);
                    return ReconciliationResult.skipped(session.playerId(), "attribution-grace");
                }
                AttributionQuery query = new AttributionQuery(session.lastContainer(), null, null);
                UnknownGainCorrelator.CorrelationResult correlation = correlator.correlate(
                        player.getUniqueId(),
                        gains,
                        expectedFlows.ledger(),
                        now,
                        query
                );
                for (ExpectedFlowLedger.Consumption consumption : correlation.explained()) {
                    ExpectedFlowCredit credit = consumption.credit();
                    String material = credit.material().equals("*") ? firstKey(gains) : credit.material();
                    if ("*".equals(material) || "AIR".equals(material)) {
                        material = firstMatching(gains, credit);
                    }
                    flows.add(ItemFlowFactory.create(
                            player,
                            now,
                            material,
                            signatureFor(current, material),
                            consumption.amount(),
                            credit.source(),
                            credit.destination(),
                            credit.confidence(),
                            credit.location() == null ? playerLocation : credit.location(),
                            credit.container(),
                            credit.note(),
                            credit.correlationId()
                    ));
                }
                List<UnknownGainCorrelator.UnexplainedGain> leftovers = correlation.unexplained();
                boolean pendingMatch = leftovers.stream().anyMatch(gain ->
                        expectedFlows.ledger().hasMatchingHint(player.getUniqueId(), gain.material(), now));
                if (!leftovers.isEmpty() && pendingMatch && !session.attributionGraceUsed()) {
                    session.setAttributionGraceUsed(true);
                    session.clearDirty();
                    requestFollowUp(player);
                    return ReconciliationResult.skipped(session.playerId(), "attribution-grace");
                }
                boolean creative = player.getGameMode() == GameMode.CREATIVE && settings.ignoreCreativeInventoryGains();
                ExpectedFlowLedger ledger = expectedFlows.ledger();
                ExpectedFlowCredit nearest = ledger.nearestHint(player.getUniqueId(), now);
                for (UnknownGainCorrelator.UnexplainedGain leftover : leftovers) {
                    if (creative) {
                        flows.add(ItemFlowFactory.create(
                                player,
                                now,
                                leftover.material(),
                                signatureFor(current, leftover.material()),
                                leftover.amount(),
                                FlowSource.CREATIVE_INVENTORY,
                                FlowDestination.PLAYER_INVENTORY,
                                SourceConfidence.TRUSTED,
                                playerLocation,
                                session.lastContainer(),
                                "Creative inventory",
                                correlationId
                        ));
                        continue;
                    }
                    session.addUnknownGain(leftover.amount());
                    long hintAge = nearest == null ? -1L : Math.max(0L, Duration.between(nearest.createdAt(), now).toMillis());
                    attribution = new AttributionDecision(
                            now,
                            "unexplained",
                            leftover.material(),
                            leftover.amount(),
                            nearest == null ? null : nearest.source().name(),
                            nearest == null ? null : (nearest.oneShot() ? "SOURCE_HINT" : "EXACT"),
                            ledger.hintCount(player.getUniqueId(), now),
                            ledger.exactCount(player.getUniqueId(), now),
                            nearest == null ? null : nearest.source().name(),
                            hintAge,
                            leftover.amount()
                    );
                    logAttribution(player, attribution);
                    flows.add(ItemFlowFactory.create(
                            player,
                            now,
                            leftover.material(),
                            signatureFor(current, leftover.material()),
                            leftover.amount(),
                            FlowSource.UNKNOWN,
                            FlowDestination.PLAYER_INVENTORY,
                            SourceConfidence.UNKNOWN,
                            playerLocation,
                            session.lastContainer(),
                            "Unexplained inventory gain",
                            correlationId
                    ));
                }
                if (leftovers.isEmpty()) {
                    session.setAttributionGraceUsed(false);
                    ExpectedFlowCredit nearestKept = ledger.nearestHint(player.getUniqueId(), now);
                    attribution = new AttributionDecision(
                            now,
                            "explained",
                            firstKey(gains),
                            gains.values().stream().mapToInt(Integer::intValue).sum(),
                            correlation.explained().isEmpty() ? null : correlation.explained().getFirst().credit().source().name(),
                            correlation.explained().isEmpty() ? null : (correlation.explained().getFirst().credit().oneShot() ? "SOURCE_HINT" : "EXACT"),
                            ledger.hintCount(player.getUniqueId(), now),
                            ledger.exactCount(player.getUniqueId(), now),
                            nearestKept == null ? null : nearestKept.source().name(),
                            nearestKept == null ? -1L : Math.max(0L, Duration.between(nearestKept.createdAt(), now).toMillis()),
                            0
                    );
                }
            }

            session.setLastSnapshot(current);
            session.setLastReconcileTime(now);
            session.clearDirty();
            if (session.discardHintsAfterReconcile() && !gains.isEmpty()) {
                expectedFlows.ledger().discardOneShot(player.getUniqueId());
                session.setDiscardHintsAfterReconcile(false);
            } else if (session.discardHintsAfterReconcile() && gains.isEmpty()) {
                session.setDiscardHintsAfterReconcile(false);
            }
            session.setLastAttribution(attribution);

            List<RiskSignal> produced = new ArrayList<>();
            for (ItemFlowEvent flow : flows) {
                traces.record(flow);
                burstDetector.recordFlow(session, flow);
            }
            for (RiskDetector detector : detectors) {
                produced.addAll(detector.detect(session, flows));
            }
            if (scanner != null && shouldScan(reason, flows, produced, session, now)) {
                long scanStart = System.nanoTime();
                produced.addAll(scanner.scanPlayer(player, correlationId, settings.maxScanDepth(), riskEngine.incidents()));
                performance.recordScan(System.nanoTime() - scanStart);
                session.setLastScanTime(now);
            }
            RiskAssessment assessment = riskEngine.assess(session.playerId(), produced, now);
            session.replaceSignals(assessment.contributingSignals());
            session.setLastAssessment(assessment);
            session.setActiveIncidents(assessment.activeIncidents());

            long elapsed = System.nanoTime() - started;
            session.setLastReconcileNanos(elapsed);
            performance.recordReconcile(elapsed);

            ReconciliationResult result = new ReconciliationResult(
                    session.playerId(),
                    reason,
                    flows,
                    produced,
                    assessment,
                    delta,
                    assessment.escalations(),
                    attribution
            );
            if (resultConsumer != null) {
                resultConsumer.accept(result);
            }
            return result;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Reconciliation failed for player " + player.getUniqueId() + " (" + reason + ")", exception);
            session.clearDirty();
            return ReconciliationResult.skipped(session.playerId(), "error");
        }
    }

    public ReconciliationResult applyHuskSync(Player player, InventorySnapshot before) {
        PlayerGuardSession session = sessions.require(player, Instant.now());
        expectedFlows.ledger().clear(player.getUniqueId());
        if (before != null) {
            session.setLastSnapshot(before);
        }
        return reconcile(player, "husksync-complete");
    }

    private void requestFollowUp(Player player) {
        Consumer<Player> next = followUp;
        if (next != null) {
            next.accept(player);
        } else {
            sessions.require(player, Instant.now()).markDirty();
        }
    }

    private void logAttribution(Player player, AttributionDecision decision) {
        if (!settings.attributionDebug() || decision == null) {
            return;
        }
        if (settings.attributionDebugUnknownOnly() && decision.unmatchedAmount() <= 0) {
            return;
        }
        logger.info("[ItemGuard attribution] player=" + player.getName()
                + " material=" + decision.material()
                + " amount=" + decision.actualAmount()
                + " reason=" + decision.reason()
                + " selected=" + decision.selectedSource()
                + " kind=" + decision.selectedKind()
                + " hints=" + decision.pendingHintsCount()
                + " credits=" + decision.expectedCreditsCount()
                + " nearestAgeMs=" + decision.nearestHintAgeMs());
    }

    private boolean shouldScan(String reason, List<ItemFlowEvent> flows, List<RiskSignal> signals, PlayerGuardSession session, Instant now) {
        return ScanGate.shouldScan(
                settings.scannerEnabled(),
                reason,
                settings.scanDebounceMillis(),
                session.lastScanTime(),
                now,
                flows,
                signals
        );
    }

    private List<ItemFlowEvent> recordAttributedGains(
            Player player,
            Instant now,
            Map<String, Integer> gains,
            FlowSource source,
            FlowDestination destination,
            SourceConfidence confidence,
            LocationRef location,
            String note,
            UUID correlationId
    ) {
        List<ItemFlowEvent> flows = new ArrayList<>();
        gains.forEach((material, amount) -> flows.add(ItemFlowFactory.create(
                player,
                now,
                material,
                ItemSignature.materialOnly(material),
                amount,
                source,
                destination,
                confidence,
                location,
                null,
                note,
                correlationId
        )));
        return flows;
    }

    private static ItemSignature signatureFor(InventorySnapshot snapshot, String material) {
        if (snapshot == null || material == null) {
            return ItemSignature.materialOnly(material == null ? "AIR" : material);
        }
        return snapshot.signatureTotals().keySet().stream()
                .filter(signature -> material.equals(signature.material()) && signature.isDetailed())
                .findFirst()
                .orElseGet(() -> ItemSignature.materialOnly(material));
    }

    private static String firstMatching(Map<String, Integer> gains, ExpectedFlowCredit credit) {
        if (credit.matches(credit.material()) && gains.containsKey(credit.material())) {
            return credit.material();
        }
        return firstKey(gains);
    }

    private static String firstKey(Map<String, Integer> map) {
        return map.isEmpty() ? "AIR" : map.keySet().iterator().next();
    }

    public record ReconciliationResult(
            UUID playerId,
            String reason,
            List<ItemFlowEvent> flows,
            List<RiskSignal> signals,
            RiskAssessment assessment,
            Map<String, Integer> delta,
            List<com.npucraft.itemguard.risk.incident.IncidentEscalation> escalations,
            AttributionDecision attribution
    ) {
        public ReconciliationResult {
            flows = flows == null ? List.of() : List.copyOf(flows);
            signals = signals == null ? List.of() : List.copyOf(signals);
            delta = delta == null ? Map.of() : Map.copyOf(delta);
            escalations = escalations == null ? List.of() : List.copyOf(escalations);
        }

        public static ReconciliationResult skipped(UUID playerId, String reason) {
            return new ReconciliationResult(playerId, reason, List.of(), List.of(), null, Map.of(), List.of(), null);
        }

        public static ReconciliationResult baseline(UUID playerId) {
            return new ReconciliationResult(playerId, "baseline", List.of(), List.of(), null, Map.of(), List.of(), null);
        }
    }
}
