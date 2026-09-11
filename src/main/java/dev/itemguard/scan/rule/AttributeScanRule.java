package dev.itemguard.scan.rule;

import dev.itemguard.item.Items;
import dev.itemguard.risk.model.Severity;
import dev.itemguard.risk.model.SignalType;
import dev.itemguard.scan.ItemScanRule;
import dev.itemguard.scan.ScanClassification;
import dev.itemguard.scan.ScanContext;
import dev.itemguard.scan.ScanFinding;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AttributeScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "attributes";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().attributesEnabled() || Items.isEmpty(stack)) {
            return List.of();
        }
        if (context.settings().ignoredAttributeMaterials().contains(stack.getType().name().toLowerCase())) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers() || meta.getAttributeModifiers() == null) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : meta.getAttributeModifiers().entries()) {
            AttributeModifier modifier = entry.getValue();
            if (modifier == null) {
                continue;
            }
            String namespace = modifier.getKey().namespace();
            if (context.settings().ignoredAttributeNamespaces().contains(namespace)) {
                continue;
            }
            boolean operationAllowed = context.settings().allowedAttributeOperations().contains(modifier.getOperation().name());
            double amount = modifier.getAmount();
            boolean excessive = Math.abs(amount) > context.settings().attributeMaxAbsolute();
            if (!operationAllowed || excessive) {
                Severity severity = severity(context.settings().attributeSeverity());
                findings.add(new ScanFinding(
                        id(),
                        SignalType.SUSPICIOUS_ATTRIBUTE,
                        ScanClassification.SUSPICIOUS,
                        severity,
                        stack.getType().name(),
                        context.signature(),
                        stack.getAmount(),
                        "Attribute " + entry.getKey().getKey().asString() + " amount=" + amount + " op=" + modifier.getOperation().name(),
                        Map.of(
                                "attribute", entry.getKey().getKey().asString(),
                                "amount", Double.toString(amount),
                                "operation", modifier.getOperation().name(),
                                "namespace", namespace
                        )
                ));
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
