package com.npucraft.itemguard.scan.rule;

import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ItemScanRule;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanContext;
import com.npucraft.itemguard.scan.ScanFinding;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PersistentDataScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "persistent-data";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().persistentDataEnabled() || Items.isEmpty(stack)) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.getKeys().isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        container.getKeys().forEach(key -> {
            String namespace = key.getNamespace().toLowerCase(Locale.ROOT);
            if (context.settings().ignoredPdcNamespaces().contains(namespace)) {
                return;
            }
            boolean denied = context.settings().deniedPdcNamespaces().contains(namespace);
            findings.add(new ScanFinding(
                    id(),
                    denied ? SignalType.SUSPICIOUS_COMPONENT : SignalType.CUSTOM_ITEM_METADATA,
                    denied ? ScanClassification.SUSPICIOUS : ScanClassification.CUSTOM,
                    denied ? Severity.SUSPICIOUS : Severity.CUSTOM,
                    stack.getType().name(),
                    context.signature(),
                    stack.getAmount(),
                    (denied ? "Denied PDC namespace " : "Custom PDC ") + key.asString(),
                    Map.of(
                            "key", key.asString(),
                            "namespace", namespace,
                            "hasValue", Boolean.toString(container.has(key, PersistentDataType.STRING) || container.has(key))
                    )
            ));
        });
        return findings;
    }
}
