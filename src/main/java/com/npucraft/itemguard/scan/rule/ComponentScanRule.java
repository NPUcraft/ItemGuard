package com.npucraft.itemguard.scan.rule;

import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ItemScanRule;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanContext;
import com.npucraft.itemguard.scan.ScanFinding;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ComponentScanRule implements ItemScanRule {

    private static final Map<String, DataComponentType> WATCHED = Map.ofEntries(
            Map.entry("ATTRIBUTE_MODIFIERS", DataComponentTypes.ATTRIBUTE_MODIFIERS),
            Map.entry("MAX_STACK_SIZE", DataComponentTypes.MAX_STACK_SIZE),
            Map.entry("ENCHANTMENTS", DataComponentTypes.ENCHANTMENTS),
            Map.entry("MAX_DAMAGE", DataComponentTypes.MAX_DAMAGE),
            Map.entry("DAMAGE", DataComponentTypes.DAMAGE),
            Map.entry("CONTAINER", DataComponentTypes.CONTAINER),
            Map.entry("BUNDLE_CONTENTS", DataComponentTypes.BUNDLE_CONTENTS),
            Map.entry("POTION_CONTENTS", DataComponentTypes.POTION_CONTENTS),
            Map.entry("CUSTOM_MODEL_DATA", DataComponentTypes.CUSTOM_MODEL_DATA),
            Map.entry("RARITY", DataComponentTypes.RARITY),
            Map.entry("TOOL", DataComponentTypes.TOOL),
            Map.entry("FOOD", DataComponentTypes.FOOD),
            Map.entry("CONSUMABLE", DataComponentTypes.CONSUMABLE),
            Map.entry("EQUIPPABLE", DataComponentTypes.EQUIPPABLE),
            Map.entry("CUSTOM_NAME", DataComponentTypes.CUSTOM_NAME),
            Map.entry("ITEM_NAME", DataComponentTypes.ITEM_NAME),
            Map.entry("LORE", DataComponentTypes.LORE)
    );

    @Override
    public String id() {
        return "components";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().componentsEnabled() || !context.settings().flagOverriddenComponents() || Items.isEmpty(stack)) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        Set<String> suspicious = context.settings().suspiciousComponents();
        Set<String> custom = context.settings().customComponents();
        WATCHED.forEach((name, type) -> {
            if (!stack.isDataOverridden(type)) {
                return;
            }
            if (suspicious.contains(name)) {
                if (isNormalGameplayComponent(name)) {
                    SignalType signal = "MAX_STACK_SIZE".equals(name) ? SignalType.CUSTOM_MAX_STACK : SignalType.COMPONENT_MODIFIED;
                    ScanClassification classification = "MAX_STACK_SIZE".equals(name) ? ScanClassification.CUSTOM : ScanClassification.INFO;
                    Severity severity = "MAX_STACK_SIZE".equals(name) ? Severity.CUSTOM : Severity.INFO;
                    findings.add(finding(stack, context, signal, classification, severity, name, "Modified component " + name));
                    return;
                }
                SignalType signal = "MAX_STACK_SIZE".equals(name) ? SignalType.CUSTOM_MAX_STACK : SignalType.SUSPICIOUS_COMPONENT;
                findings.add(finding(stack, context, signal, ScanClassification.SUSPICIOUS, Severity.SUSPICIOUS, name, "Overridden component " + name));
            } else if (custom.contains(name)) {
                findings.add(finding(stack, context, SignalType.CUSTOM_ITEM_METADATA, ScanClassification.CUSTOM, Severity.CUSTOM, name, "Custom component " + name));
            } else {
                findings.add(finding(stack, context, SignalType.COMPONENT_MODIFIED, ScanClassification.INFO, Severity.INFO, name, "Modified component " + name));
            }
        });
        return findings;
    }

    private static boolean isNormalGameplayComponent(String name) {
        return "ENCHANTMENTS".equals(name)
                || "DAMAGE".equals(name)
                || "MAX_DAMAGE".equals(name)
                || "CUSTOM_NAME".equals(name)
                || "ITEM_NAME".equals(name)
                || "LORE".equals(name)
                || "MAX_STACK_SIZE".equals(name);
    }

    private ScanFinding finding(
            ItemStack stack,
            ScanContext context,
            SignalType type,
            ScanClassification classification,
            Severity severity,
            String component,
            String description
    ) {
        return new ScanFinding(
                id(),
                type,
                classification,
                severity,
                stack.getType().name(),
                context.signature(),
                stack.getAmount(),
                description,
                Map.of("component", component)
        );
    }
}
