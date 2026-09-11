package dev.itemguard.flow;

import dev.itemguard.config.PluginSettings;
import dev.itemguard.config.RiskSettings;
import dev.itemguard.flow.model.FlowDestination;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.InventorySnapshot;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.flow.model.LocationRef;
import dev.itemguard.flow.model.SourceConfidence;
import dev.itemguard.item.ItemSignature;
import dev.itemguard.risk.RiskEngine;
import dev.itemguard.risk.detector.BurstDetector;
import dev.itemguard.risk.detector.RiskDetector;
import dev.itemguard.risk.model.RiskAssessment;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.scan.IllegalItemScanner;
import dev.itemguard.session.PlayerGuardSession;
import dev.itemguard.session.SessionManager;
import dev.itemguard.trace.TraceService;
import dev.itemguard.util.PerformanceStats;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

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
            if ("husksync-complete".equals(reason)) {
                expectedFlows.ledger().clear(player.getUniqueId());
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
                UnknownGainCorrelator.CorrelationResult correlation = correlator.correlate(
                        player.getUniqueId(),
                        gains,
                        expectedFlows.ledger(),
                        now
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
                            ItemSignature.materialOnly(material),
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
                boolean creative = player.getGameMode() == GameMode.CREATIVE && settings.ignoreCreativeInventoryGains();
                for (UnknownGainCorrelator.UnexplainedGain leftover : correlation.unexplained()) {
                    if (creative) {
                        flows.add(ItemFlowFactory.create(
                                player,
                                now,
                                leftover.material(),
                                ItemSignature.materialOnly(leftover.material()),
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
                    flows.add(ItemFlowFactory.create(
                            player,
                            now,
                            leftover.material(),
                            ItemSignature.materialOnly(leftover.material()),
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
            }

            session.setLastSnapshot(current);
            session.setLastReconcileTime(now);
            session.clearDirty();
            if (session.discardHintsAfterReconcile()) {
                expectedFlows.ledger().discardOneShot(player.getUniqueId());
                session.setDiscardHintsAfterReconcile(false);
            }

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
                produced.addAll(scanner.scanPlayer(player, correlationId, settings.maxScanDepth()));
                performance.recordScan(System.nanoTime() - scanStart);
                session.setLastScanTime(now);
            }
            List<RiskSignal> merged = riskEngine.merge(session.signals(), produced, now);
            session.replaceSignals(merged);
            RiskAssessment assessment = riskEngine.assess(session.playerId(), merged, now);
            session.setLastAssessment(assessment);

            long elapsed = System.nanoTime() - started;
            session.setLastReconcileNanos(elapsed);
            performance.recordReconcile(elapsed);

            ReconciliationResult result = new ReconciliationResult(session.playerId(), reason, flows, produced, assessment, delta);
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
            Map<String, Integer> delta
    ) {
        public static ReconciliationResult skipped(UUID playerId, String reason) {
            return new ReconciliationResult(playerId, reason, List.of(), List.of(), null, Map.of());
        }

        public static ReconciliationResult baseline(UUID playerId) {
            return new ReconciliationResult(playerId, "baseline", List.of(), List.of(), null, Map.of());
        }
    }
}
