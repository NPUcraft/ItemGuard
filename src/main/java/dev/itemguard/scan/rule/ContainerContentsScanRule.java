package dev.itemguard.scan.rule;

import dev.itemguard.item.Items;
import dev.itemguard.item.NestedContents;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.scan.ItemScanRule;
import dev.itemguard.scan.ScanClassification;
import dev.itemguard.scan.ScanContext;
import dev.itemguard.scan.ScanFinding;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Recurses into shulker/bundle contents through Paper APIs only (ItemMeta + Data Components).
 */
public final class ContainerContentsScanRule implements ItemScanRule {

    private final BiFunction<ItemStack, ScanContext, List<ScanFinding>> nestedScanner;

    public ContainerContentsScanRule(BiFunction<ItemStack, ScanContext, List<ScanFinding>> nestedScanner) {
        this.nestedScanner = nestedScanner;
    }

    @Override
    public String id() {
        return "container-contents";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().containerContentsEnabled() || !context.settings().scanInnerItems() || Items.isEmpty(stack)) {
            return List.of();
        }
        if (!context.canRecurse()) {
            return List.of();
        }
        List<ItemStack> inner = NestedContents.children(stack);
        if (inner.isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        ScanContext child = context.deeper();
        for (ItemStack nested : inner) {
            if (Items.isEmpty(nested)) {
                continue;
            }
            List<ScanFinding> nestedFindings = nestedScanner.apply(nested, child);
            findings.addAll(nestedFindings);
            boolean serious = nestedFindings.stream().anyMatch(finding ->
                    finding.classification() == ScanClassification.INVALID || finding.classification() == ScanClassification.SUSPICIOUS);
            if (serious) {
                findings.add(new ScanFinding(
                        id(),
                        SignalType.SUSPICIOUS_CONTAINER_CONTENTS,
                        ScanClassification.SUSPICIOUS,
                        Severity.SUSPICIOUS,
                        stack.getType().name(),
                        context.signature(),
                        stack.getAmount(),
                        "Container holds suspicious " + nested.getType().name(),
                        Map.of(
                                "innerMaterial", nested.getType().name(),
                                "depth", Integer.toString(child.depth())
                        )
                ));
            }
        }
        return findings;
    }
}
