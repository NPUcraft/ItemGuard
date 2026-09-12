package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.config.RiskSettings;
import com.npucraft.itemguard.config.ScannerSettings;
import com.npucraft.itemguard.item.ItemSignatures;
import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.risk.incident.PlayerIncidentTracker;
import com.npucraft.itemguard.risk.model.RiskSignal;
import com.npucraft.itemguard.scan.rule.AttributeScanRule;
import com.npucraft.itemguard.scan.rule.ComponentScanRule;
import com.npucraft.itemguard.scan.rule.ContainerContentsScanRule;
import com.npucraft.itemguard.scan.rule.CustomMetadataScanRule;
import com.npucraft.itemguard.scan.rule.DurabilityScanRule;
import com.npucraft.itemguard.scan.rule.EnchantmentScanRule;
import com.npucraft.itemguard.scan.rule.PersistentDataScanRule;
import com.npucraft.itemguard.scan.rule.StackSizeScanRule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IllegalItemScanner {

    private final ItemSignatures signatures;
    private final Logger logger;
    private final List<ItemScanRule> rules;
    private final ScannerFindingObserver observer;
    private volatile ScannerSettings scannerSettings;

    public IllegalItemScanner(
            ItemSignatures signatures,
            Logger logger,
            ScannerSettings scannerSettings,
            RiskSettings riskSettings
    ) {
        this.signatures = signatures;
        this.logger = logger;
        this.scannerSettings = scannerSettings;
        this.observer = new ScannerFindingObserver(riskSettings);
        this.rules = List.of(
                new EnchantmentScanRule(),
                new StackSizeScanRule(),
                new AttributeScanRule(),
                new DurabilityScanRule(),
                new ComponentScanRule(),
                new PersistentDataScanRule(),
                new CustomMetadataScanRule(),
                new ContainerContentsScanRule(this::scanStack)
        );
    }

    public void updateSettings(ScannerSettings scannerSettings, RiskSettings riskSettings) {
        this.scannerSettings = scannerSettings;
        this.observer.updateSettings(riskSettings);
    }

    public ScannerFindingTracker findings() {
        return observer.findings();
    }

    public void clearPlayer(UUID playerId) {
        observer.clearPlayer(playerId);
    }

    public List<RiskSignal> scanPlayer(Player player, UUID correlationId, int maxDepth) {
        return scanPlayer(player, correlationId, maxDepth, null);
    }

    public List<RiskSignal> scanPlayer(Player player, UUID correlationId, int maxDepth, PlayerIncidentTracker incidents) {
        if (!scannerSettings.enabled()) {
            return List.of();
        }
        Instant now = Instant.now();
        UUID playerId = player.getUniqueId();
        List<ScanFinding> found = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        scanContents(inventory.getStorageContents(), found, maxDepth);
        scanContents(inventory.getArmorContents(), found, maxDepth);
        scanContents(inventory.getExtraContents(), found, maxDepth);
        return observer.observe(playerId, found, correlationId, now, incidents);
    }

    /**
     * Test helper: apply finding-state rules without a live Player inventory.
     */
    public List<RiskSignal> observeFindings(UUID playerId, List<ScanFinding> found, UUID correlationId, Instant now) {
        return observer.observe(playerId, found, correlationId, now, null);
    }

    public List<RiskSignal> observeFindings(
            UUID playerId,
            List<ScanFinding> found,
            UUID correlationId,
            Instant now,
            PlayerIncidentTracker incidents
    ) {
        return observer.observe(playerId, found, correlationId, now, incidents);
    }

    public List<ScanFinding> scanInventory(Player player, int maxDepth) {
        List<ScanFinding> found = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        scanContents(inventory.getStorageContents(), found, maxDepth);
        scanContents(inventory.getArmorContents(), found, maxDepth);
        scanContents(inventory.getExtraContents(), found, maxDepth);
        return found;
    }

    public List<ScanFinding> scanStack(ItemStack stack, ScanContext context) {
        if (Items.isEmpty(stack)) {
            return List.of();
        }
        List<ScanFinding> found = new ArrayList<>();
        for (ItemScanRule rule : rules) {
            try {
                found.addAll(rule.scan(stack, context));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Scanner rule " + rule.id() + " failed for " + stack.getType(), exception);
            }
        }
        return found;
    }

    private void scanContents(ItemStack[] contents, List<ScanFinding> findings, int maxDepth) {
        if (contents == null) {
            return;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (Items.isEmpty(stack)) {
                continue;
            }
            ScanContext context = new ScanContext(scannerSettings, signatures.of(stack), 0, maxDepth);
            findings.addAll(scanStack(stack, context));
        }
    }
}
