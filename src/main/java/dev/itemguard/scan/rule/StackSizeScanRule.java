package dev.itemguard.scan.rule;

import dev.itemguard.item.Items;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.scan.ItemScanRule;
import dev.itemguard.scan.ScanClassification;
import dev.itemguard.scan.ScanContext;
import dev.itemguard.scan.ScanFinding;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class StackSizeScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "stack-size";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().stackEnabled() || !context.settings().flagOversized() || Items.isEmpty(stack)) {
            return List.of();
        }
        int max = stack.getMaxStackSize();
        if (stack.getAmount() <= max) {
            return List.of();
        }
        return List.of(new ScanFinding(
                id(),
                SignalType.OVERSIZED_STACK,
                ScanClassification.INVALID,
                Severity.INVALID,
                stack.getType().name(),
                context.signature(),
                stack.getAmount(),
                "Stack amount " + stack.getAmount() + " exceeds ItemStack max " + max,
                Map.of(
                        "amount", Integer.toString(stack.getAmount()),
                        "maxStackSize", Integer.toString(max),
                        "materialMax", Integer.toString(stack.getType().getMaxStackSize())
                )
        ));
    }
}
