package com.npucraft.itemguard.scan.rule;

import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ItemScanRule;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanContext;
import com.npucraft.itemguard.scan.ScanFinding;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DurabilityScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "durability";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().durabilityEnabled() || Items.isEmpty(stack)) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return List.of();
        }
        int damage = damageable.getDamage();
        int max = stack.getType().getMaxDurability();
        if (damageable.hasMaxDamage()) {
            max = damageable.getMaxDamage();
        }
        List<ScanFinding> findings = new ArrayList<>();
        if (context.settings().flagNegativeDurability() && damage < 0) {
            findings.add(finding(stack, context, damage, max, "Negative damage " + damage));
        }
        if (context.settings().flagAboveMaxDurability() && max > 0 && damage > max) {
            findings.add(finding(stack, context, damage, max, "Damage " + damage + " exceeds max " + max));
        }
        return findings;
    }

    private ScanFinding finding(ItemStack stack, ScanContext context, int damage, int max, String description) {
        return new ScanFinding(
                id(),
                SignalType.ABNORMAL_DURABILITY,
                ScanClassification.INVALID,
                Severity.INVALID,
                stack.getType().name(),
                context.signature(),
                stack.getAmount(),
                description,
                Map.of(
                        "damage", Integer.toString(damage),
                        "maxDamage", Integer.toString(max)
                )
        );
    }
}
