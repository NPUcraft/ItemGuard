package dev.itemguard.risk.detector;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.flow.model.FlowSource;
import dev.itemguard.flow.model.ItemFlowEvent;
import dev.itemguard.item.ItemValueRegistry;
import dev.itemguard.risk.ItemGainWindow;
import dev.itemguard.risk.RiskSignals;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.session.PlayerGuardSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Burst detectors look at confirmed incoming volume/value. Explainable chest loot can still burst.
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

        int rapidAmount = window.totalAmount(now, settings.rapidGainWindow());
        if (rapidAmount >= settings.rapidGainAmount()) {
            ItemGainWindow.Gain sample = latest(window, now, settings.rapidGainWindow());
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.RAPID_ITEM_GAIN,
                    settings,
                    Severity.SUSPICIOUS,
                    sample == null ? "*" : sample.material(),
                    null,
                    rapidAmount,
                    firstCorrelation(burstable),
                    null,
                    rapidAmount + " items gained within " + format(settings.rapidGainWindow()),
                    Map.of("windowMillis", Long.toString(settings.rapidGainWindow().toMillis()))
            ));
        }

        int valueScore = window.totalValue(now, settings.highValueBurstWindow());
        if (valueScore >= settings.highValueBurstScore()) {
            ItemGainWindow.Gain hottest = hottest(window, now, settings.highValueBurstWindow());
            String material = hottest == null ? "*" : hottest.material();
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
                    null,
                    amount,
                    firstCorrelation(burstable),
                    null,
                    amount + " " + material + " gained within " + format(settings.highValueBurstWindow()),
                    Map.of(
                            "windowMillis", Long.toString(settings.highValueBurstWindow().toMillis()),
                            "valueScore", Integer.toString(valueScore)
                    )
            ));
        }

        window.signatureTotals(now, settings.repeatedIdenticalWindow()).forEach((hash, amount) -> {
            if (amount < settings.repeatedIdenticalAmount() || "material-only".equals(hash) || hash.startsWith("fallback:")) {
                return;
            }
            ItemGainWindow.Gain sample = window.inWindow(now, settings.repeatedIdenticalWindow()).stream()
                    .filter(gain -> hash.equals(gain.signatureHash()))
                    .findFirst()
                    .orElse(null);
            String material = sample == null ? "*" : sample.material();
            signals.add(RiskSignals.create(
                    session.playerId(),
                    SignalType.REPEATED_IDENTICAL_ITEMS,
                    settings,
                    Severity.SUSPICIOUS,
                    material,
                    null,
                    amount,
                    firstCorrelation(burstable),
                    null,
                    amount + " identical " + material + " stacks within " + format(settings.repeatedIdenticalWindow()),
                    Map.of("signature", hash)
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
        return window.inWindow(now, duration).stream()
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
