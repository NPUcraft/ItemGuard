package dev.itemguard.scan;

import dev.itemguard.config.RiskSettings;
import dev.itemguard.config.ScannerSettings;
import dev.itemguard.item.ItemSignatures;
import dev.itemguard.item.Items;
import dev.itemguard.risk.RiskSignals;
import dev.itemguard.risk.model.RiskSignal;
import dev.itemguard.scan.rule.AttributeScanRule;
import dev.itemguard.scan.rule.ComponentScanRule;
import dev.itemguard.scan.rule.ContainerContentsScanRule;
import dev.itemguard.scan.rule.CustomMetadataScanRule;
import dev.itemguard.scan.rule.DurabilityScanRule;
import dev.itemguard.scan.rule.EnchantmentScanRule;
import dev.itemguard.scan.rule.PersistentDataScanRule;
import dev.itemguard.scan.rule.StackSizeScanRule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IllegalItemScanner {

    private final ItemSignatures signatures;
    private final Logger logger;
    private final List<ItemScanRule> rules;
    private volatile ScannerSettings scannerSettings;
    private volatile RiskSettings riskSettings;

    public IllegalItemScanner(
            ItemSignatures signatures,
            Logger logger,
            ScannerSettings scannerSettings,
            RiskSettings riskSettings
    ) {
        this.signatures = signatures;
        this.logger = logger;
        this.scannerSettings = scannerSettings;
        this.riskSettings = riskSettings;
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
        this.riskSettings = riskSettings;
    }

    public List<RiskSignal> scanPlayer(Player player, UUID correlationId, int maxDepth) {
        if (!scannerSettings.enabled()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        scanContents(inventory.getStorageContents(), findings, maxDepth);
        scanContents(inventory.getArmorContents(), findings, maxDepth);
        scanContents(inventory.getExtraContents(), findings, maxDepth);
        int suspicious = 0;
        List<RiskSignal> signals = new ArrayList<>();
        for (ScanFinding finding : findings) {
            if (finding.classification() != ScanClassification.CUSTOM && finding.classification() != ScanClassification.INFO) {
                suspicious++;
            }
            signals.add(toSignal(player.getUniqueId(), finding, correlationId));
        }
        return signals;
    }

    public List<ScanFinding> scanInventory(Player player, int maxDepth) {
        List<ScanFinding> findings = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        scanContents(inventory.getStorageContents(), findings, maxDepth);
        scanContents(inventory.getArmorContents(), findings, maxDepth);
        scanContents(inventory.getExtraContents(), findings, maxDepth);
        return findings;
    }

    public List<ScanFinding> scanStack(ItemStack stack, ScanContext context) {
        if (Items.isEmpty(stack)) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        for (ItemScanRule rule : rules) {
            try {
                findings.addAll(rule.scan(stack, context));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Scanner rule " + rule.id() + " failed for " + stack.getType(), exception);
            }
        }
        return findings;
    }

    private void scanContents(ItemStack[] contents, List<ScanFinding> findings, int maxDepth) {
        if (contents == null) {
            return;
        }
        for (ItemStack stack : contents) {
            if (Items.isEmpty(stack)) {
                continue;
            }
            ScanContext context = new ScanContext(scannerSettings, signatures.of(stack), 0, maxDepth);
            findings.addAll(scanStack(stack, context));
        }
    }

    private RiskSignal toSignal(UUID playerId, ScanFinding finding, UUID correlationId) {
        return RiskSignals.create(
                playerId,
                finding.signalType(),
                riskSettings,
                finding.severity(),
                finding.material(),
                finding.signature(),
                finding.amount(),
                correlationId,
                null,
                finding.description(),
                finding.evidence()
        );
    }
}
