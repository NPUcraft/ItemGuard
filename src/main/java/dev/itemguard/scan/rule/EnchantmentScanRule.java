package dev.itemguard.scan.rule;

import dev.itemguard.item.Items;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.scan.ItemScanRule;
import dev.itemguard.scan.ScanClassification;
import dev.itemguard.scan.ScanContext;
import dev.itemguard.scan.ScanFinding;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnchantmentScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "enchantments";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().enchantmentsEnabled() || Items.isEmpty(stack)) {
            return List.of();
        }
        Map<Enchantment, Integer> enchantments = new HashMap<>(stack.getEnchantments());
        if (stack.getItemMeta() instanceof EnchantmentStorageMeta storage) {
            enchantments.putAll(storage.getStoredEnchants());
        }
        List<ScanFinding> findings = new ArrayList<>();
        if (context.settings().flagOverLevel()) {
            enchantments.forEach((enchantment, level) -> {
                int max = enchantment.getMaxLevel();
                if (level > max) {
                    findings.add(new ScanFinding(
                            id(),
                            SignalType.OVER_LEVEL_ENCHANTMENT,
                            ScanClassification.INVALID,
                            Severity.INVALID,
                            stack.getType().name(),
                            context.signature(),
                            stack.getAmount(),
                            enchantment.getKey().asString() + " " + level + " exceeds max " + max,
                            Map.of(
                                    "enchantment", enchantment.getKey().asString(),
                                    "level", Integer.toString(level),
                                    "maxLevel", Integer.toString(max)
                            )
                    ));
                }
            });
        }
        if (context.settings().flagConflicting()) {
            List<Enchantment> listed = List.copyOf(enchantments.keySet());
            for (int i = 0; i < listed.size(); i++) {
                for (int j = i + 1; j < listed.size(); j++) {
                    Enchantment left = listed.get(i);
                    Enchantment right = listed.get(j);
                    if (left.conflictsWith(right)) {
                        Severity severity = severity(context.settings().conflictingSeverity());
                        findings.add(new ScanFinding(
                                id(),
                                SignalType.CONFLICTING_ENCHANTMENTS,
                                severity == Severity.INVALID ? ScanClassification.INVALID : ScanClassification.SUSPICIOUS,
                                severity,
                                stack.getType().name(),
                                context.signature(),
                                stack.getAmount(),
                                left.getKey().asString() + " conflicts with " + right.getKey().asString(),
                                Map.of(
                                        "left", left.getKey().asString(),
                                        "right", right.getKey().asString()
                                )
                        ));
                    }
                }
            }
        }
        return findings;
    }

    private static Severity severity(String raw) {
        try {
            return Severity.valueOf(raw);
        } catch (RuntimeException exception) {
            return Severity.SUSPICIOUS;
        }
    }
}
