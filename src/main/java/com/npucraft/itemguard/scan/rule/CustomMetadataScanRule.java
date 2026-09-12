package com.npucraft.itemguard.scan.rule;

import com.npucraft.itemguard.item.Items;
import com.npucraft.itemguard.risk.model.Severity;
import com.npucraft.itemguard.risk.model.SignalType;
import com.npucraft.itemguard.scan.ItemScanRule;
import com.npucraft.itemguard.scan.ScanClassification;
import com.npucraft.itemguard.scan.ScanContext;
import com.npucraft.itemguard.scan.ScanFinding;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomMetadataScanRule implements ItemScanRule {

    @Override
    public String id() {
        return "custom-metadata";
    }

    @Override
    public List<ScanFinding> scan(ItemStack stack, ScanContext context) {
        if (!context.settings().customMetadataEnabled() || Items.isEmpty(stack)) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        if (context.settings().flagCustomName() && meta.hasDisplayName()) {
            findings.add(custom(stack, context, "custom-name", "Custom display name present"));
        }
        if (context.settings().flagLore() && meta.hasLore()) {
            findings.add(custom(stack, context, "lore", "Custom lore present"));
        }
        if (context.settings().flagCustomModelData() && stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            findings.add(custom(stack, context, "custom-model-data", "CustomModelData present"));
        }
        return findings;
    }

    private ScanFinding custom(ItemStack stack, ScanContext context, String kind, String description) {
        return new ScanFinding(
                id(),
                SignalType.CUSTOM_ITEM_METADATA,
                ScanClassification.CUSTOM,
                Severity.CUSTOM,
                stack.getType().name(),
                context.signature(),
                stack.getAmount(),
                description,
                Map.of("kind", kind)
        );
    }
}
