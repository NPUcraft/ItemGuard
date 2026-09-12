package com.npucraft.itemguard.risk.detector;

import com.npucraft.itemguard.config.DetectorGateSettings;
import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.item.ItemValueRegistry;
import com.npucraft.itemguard.risk.ItemGainWindow;
import com.npucraft.itemguard.risk.RiskSignals;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.session.PlayerGuardSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Burst detectors look at confirmed incoming volume/value. High-value burst requires
 * a minimum per-item value so bulk dirt cannot qualify.
 */
public final class BurstDetector implements RiskDetector {

    private volatile RiskSettings settings;
    private volatile ItemValueRegistry values;

    public BurstDetector(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    public void updateSettings(RiskSettings settings, ItemValueRegistry values) {
        this.settings = settings;
        this.values = values;
    }

    @Override
    public List<RiskSignal> detect(PlayerGuardSession session, List<ItemFlowEvent> newFlows) {
        List<ItemFlowEvent> burstable = burstable(newFlows);
        if (!hasIncomingGain(burstable)) {
            return List.of();
        }
        Instant now = Instant.now();
        ItemGainWindow window = session.gainWindow();
        window.purge(now, settings.repeatedIdenticalWindow());
        List<RiskSignal> signals = new ArrayList<>();
        DetectorGateSettings gates = settings.detectorGates();

        int rapidAmount = window.unexplainedAmount(now, settings.rapidGainWindow());
        List<ItemGainWindow.Gain> rapidRecent = window.inWindow(now, settings.rapidGainWindow()).stream()
                .filter(ItemGainWindow.Gain::unexplained)
                .toList();
        boolean rapidUnknown = !rapidRecent.isEmpty() || burstable.stream().anyMatch(ItemFlowEvent::isUnexplained);
        if (rapidAmount >= settings.rapidGainAmount()) {
            int maxValue = rapidRecent.stream().mapToInt(ItemGainWindow.Gain::valueWeight).max().orElse(0);
            ItemGainWindow.Gain hottest = hottest(window, now, settings.rapidGainWindow());
            String material = hottest == null ? (latest(window, now, settings.rapidGainWindow()) == null
                    ? "*" : latest(window, now, settings.rapidGainWindow()).material()) : hottest.material();
            int score = 0;
            if (rapidUnknown && maxValue <= gates.rapidLowValueMaxItemValue()) {
                score = gates.rapidLowValueScore();
            } else if (rapidUnknown) {
                score = settings.score(SignalType.RAPID_ITEM_GAIN);
            }
            if (score > 0) {
                signals.add(RiskSignals.create(
                        session.playerId(),
                        SignalType.RAPID_ITEM_GAIN,
                        settings,
                        Severity.SUSPICIOUS,
                        material,
                        null,
                        rapidAmount,
                        firstCorrelation(burstable),
                        null,
                        rapidAmount + " items gained within " + format(settings.rapidGainWindow()),
                        Map.of(
                                "windowMillis", Long.toString(settings.rapidGainWindow().toMillis()),
                                "maxItemValue", Integer.toString(maxValue)
                        ),
                        score
                ));
            }
        }

        int valueScore = window.unexplainedValue(now, settings.highValueBurstWindow());
        ItemGainWindow.Gain hottest = hottestUnexplained(window, now, settings.highValueBurstWindow());
        int hottestValue = hottest == null ? 0 : hottest.valueWeight();
        boolean burstUnknown = hottest != null || burstable.stream().anyMatch(ItemFlowEvent::isUnexplained);
        if (burstUnknown && valueScore >= settings.highValueBurstScore() && hottestValue >= gates.highValueBurstMinimumItemValue()) {
            String material = hottest.material();
            int amount = window.inWindow(now, settings.highValueBurstWindow()).stream()
                    .filter(gain -> material.equals(gain.material()))
                    .mapToInt(ItemGainWindow.Gain::amount)
                    .sum();
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.HIGH_VALUE_ITEM_BURST,
                    settings,
                    Severity.HIGH,
                    material,
                    hottest.signatureHash() == null ? null : new com.npucraft.itemguard.item.ItemSignature(material, hottest.signatureHash()),
                    amount,
                    firstCorrelation(burstable),
                    null,
                    amount + " " + material + " gained within " + format(settings.highValueBurstWindow()),
                    Map.of(
                            "windowMillis", Long.toString(settings.highValueBurstWindow().toMillis()),
                            "valueScore", Integer.toString(valueScore),
                            "itemValue", Integer.toString(hottestValue)
                    )
            ));
        }

        window.unexplainedSignatureTotals(now, settings.repeatedIdenticalWindow()).forEach((hash, amount) -> {
            if (amount < settings.repeatedIdenticalAmount() || "material-only".equals(hash) || hash.startsWith("fallback:")) {
                return;
            }
            ItemGainWindow.Gain sample = window.inWindow(now, settings.repeatedIdenticalWindow()).stream()
                    .filter(gain -> hash.equals(gain.signatureHash()))
                    .findFirst()
                    .orElse(null);
            if (sample == null || sample.valueWeight() < gates.repeatedIdenticalMinimumItemValue()) {
                return;
            }
            String material = sample.material();
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.REPEATED_IDENTICAL_ITEMS,
                    settings,
                    Severity.SUSPICIOUS,
                    material,
                    new com.npucraft.itemguard.item.ItemSignature(material, hash),
                    amount,
                    firstCorrelation(burstable),
                    null,
                    amount + " identical " + material + " stacks within " + format(settings.repeatedIdenticalWindow()),
                    Map.of("signature", hash, "itemValue", Integer.toString(sample.valueWeight()))
            ));
        });
        return signals;
    }

    public static boolean hasIncomingGain(List<ItemFlowEvent> newFlows) {
        return newFlows != null && newFlows.stream().anyMatch(ItemFlowEvent::isGain);
    }

    /**
     * HuskSync restore can legitimately apply a whole high-value inventory at once.
     * That is {@code HUSKSYNC_DATA_APPLY}, not a burst the player produced on this server.
     */
    public static boolean isHuskSyncApply(ItemFlowEvent flow) {
        return flow != null && flow.source() == FlowSource.HUSKSYNC_DATA_APPLY;
    }

    static List<ItemFlowEvent> burstable(List<ItemFlowEvent> newFlows) {
        if (newFlows == null || newFlows.isEmpty()) {
            return List.of();
        }
        return newFlows.stream().filter(flow -> !isHuskSyncApply(flow)).toList();
    }

    public void recordFlow(PlayerGuardSession session, ItemFlowEvent flow) {
        if (!flow.isGain() || isHuskSyncApply(flow)) {
            return;
        }
        String hash = flow.signature() == null ? null : flow.signature().hash();
        session.gainWindow().record(
                flow.timestamp(),
                flow.material(),
                flow.amountDelta(),
                values.valueOf(flow.material()),
                flow.isUnexplained(),
                hash,
                flow.source().name()
        );
    }

    private static ItemGainWindow.Gain latest(ItemGainWindow window, Instant now, Duration duration) {
        List<ItemGainWindow.Gain> gains = window.inWindow(now, duration);
        return gains.isEmpty() ? null : gains.get(gains.size() - 1);
    }

    private static ItemGainWindow.Gain hottest(ItemGainWindow window, Instant now, Duration duration) {
        return hottestUnexplained(window, now, duration);
    }

    private static ItemGainWindow.Gain hottestUnexplained(ItemGainWindow window, Instant now, Duration duration) {
        return window.inWindow(now, duration).stream()
                .filter(ItemGainWindow.Gain::unexplained)
                .max(Comparator.comparingInt(gain -> gain.amount() * gain.valueWeight()))
                .orElse(null);
    }

    private static java.util.UUID firstCorrelation(List<ItemFlowEvent> flows) {
        return flows.stream().map(ItemFlowEvent::correlationId).filter(id -> id != null).findFirst().orElse(null);
    }

    private static String format(Duration duration) {
        double seconds = duration.toMillis() / 1000.0;
        return seconds + "s";
    }
}
