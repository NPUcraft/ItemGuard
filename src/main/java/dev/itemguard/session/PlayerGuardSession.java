package dev.itemguard.session;

import dev.itemguard.flow.model.ContainerIdentity;
import dev.itemguard.flow.model.InventorySnapshot;
import dev.itemguard.risk.ItemGainWindow;
import dev.itemguard.risk.RiskLevel;
import dev.itemguard.risk.model.RiskAssessment;
import dev.itemguard.risk.model.RiskSignal;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerGuardSession {

    private final UUID playerId;
    private final Instant createdAt;
    private final HuskSyncTransaction huskSync = new HuskSyncTransaction();
    private final ItemGainWindow gainWindow = new ItemGainWindow();
    private final ShulkerActivityTracker shulker = new ShulkerActivityTracker();
    private final List<RiskSignal> signals = new ArrayList<>();

    private String playerName;
    private InventorySnapshot lastSnapshot;
    private Map<String, Integer> lastDiff = Map.of();
    private Instant lastReconcileTime;
    private Instant lastScanTime;
    private RiskAssessment lastAssessment;
    private int currentRiskScore;
    private RiskLevel currentRiskLevel = RiskLevel.NORMAL;
    private boolean dirty;
    private boolean reconcileScheduled;
    private ContainerIdentity lastContainer;
    private int unknownGainCount;
    private int suspiciousItemCount;
    private boolean discardHintsAfterReconcile;
    private long lastReconcileNanos;

    public PlayerGuardSession(Player player, Instant createdAt) {
        this(player.getUniqueId(), player.getName(), createdAt);
    }

    public PlayerGuardSession(UUID playerId, String playerName, Instant createdAt) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.createdAt = createdAt;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void updateName(String playerName) {
        this.playerName = playerName;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public HuskSyncTransaction huskSync() {
        return huskSync;
    }

    public ItemGainWindow gainWindow() {
        return gainWindow;
    }

    public ShulkerActivityTracker shulker() {
        return shulker;
    }

    public InventorySnapshot lastSnapshot() {
        return lastSnapshot;
    }

    public void setLastSnapshot(InventorySnapshot snapshot) {
        this.lastSnapshot = snapshot;
    }

    public Map<String, Integer> lastDiff() {
        return lastDiff;
    }

    public void setLastDiff(Map<String, Integer> lastDiff) {
        this.lastDiff = lastDiff == null ? Map.of() : Map.copyOf(lastDiff);
    }

    public Instant lastReconcileTime() {
        return lastReconcileTime;
    }

    public void setLastReconcileTime(Instant lastReconcileTime) {
        this.lastReconcileTime = lastReconcileTime;
    }

    public Instant lastScanTime() {
        return lastScanTime;
    }

    public void setLastScanTime(Instant lastScanTime) {
        this.lastScanTime = lastScanTime;
    }

    public List<RiskSignal> signals() {
        return signals;
    }

    public void replaceSignals(List<RiskSignal> next) {
        signals.clear();
        signals.addAll(next);
    }

    public RiskAssessment lastAssessment() {
        return lastAssessment;
    }

    public void setLastAssessment(RiskAssessment assessment) {
        this.lastAssessment = assessment;
        if (assessment != null) {
            this.currentRiskScore = assessment.score();
            this.currentRiskLevel = assessment.level();
        }
    }

    public int currentRiskScore() {
        return currentRiskScore;
    }

    public RiskLevel currentRiskLevel() {
        return currentRiskLevel;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean takeReconcileSchedule() {
        if (reconcileScheduled) {
            dirty = true;
            return false;
        }
        reconcileScheduled = true;
        dirty = true;
        return true;
    }

    public void releaseReconcileSchedule() {
        reconcileScheduled = false;
    }

    public boolean reconcileScheduled() {
        return reconcileScheduled;
    }

    public ContainerIdentity lastContainer() {
        return lastContainer;
    }

    public void setLastContainer(ContainerIdentity lastContainer) {
        this.lastContainer = lastContainer;
    }

    public int unknownGainCount() {
        return unknownGainCount;
    }

    public void addUnknownGain(int amount) {
        this.unknownGainCount += Math.max(0, amount);
    }

    public int suspiciousItemCount() {
        return suspiciousItemCount;
    }

    public void setSuspiciousItemCount(int suspiciousItemCount) {
        this.suspiciousItemCount = suspiciousItemCount;
    }

    public boolean discardHintsAfterReconcile() {
        return discardHintsAfterReconcile;
    }

    public void setDiscardHintsAfterReconcile(boolean discardHintsAfterReconcile) {
        this.discardHintsAfterReconcile = discardHintsAfterReconcile;
    }

    public long lastReconcileNanos() {
        return lastReconcileNanos;
    }

    public void setLastReconcileNanos(long lastReconcileNanos) {
        this.lastReconcileNanos = lastReconcileNanos;
    }
}
