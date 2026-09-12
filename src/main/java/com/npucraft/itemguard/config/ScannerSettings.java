package com.npucraft.itemguard.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ScannerSettings(
        boolean enabled,
        boolean autoRemove,
        Set<String> ignoredNamespaces,
        boolean enchantmentsEnabled,
        boolean flagOverLevel,
        boolean flagConflicting,
        String conflictingSeverity,
        boolean stackEnabled,
        boolean flagOversized,
        boolean attributesEnabled,
        double attributeMaxAbsolute,
        Set<String> allowedAttributeOperations,
        Set<String> ignoredAttributeMaterials,
        Set<String> ignoredAttributeNamespaces,
        String attributeSeverity,
        boolean durabilityEnabled,
        boolean flagNegativeDurability,
        boolean flagAboveMaxDurability,
        boolean componentsEnabled,
        boolean flagOverriddenComponents,
        Set<String> suspiciousComponents,
        Set<String> customComponents,
        boolean persistentDataEnabled,
        Set<String> deniedPdcNamespaces,
        Set<String> ignoredPdcNamespaces,
        boolean containerContentsEnabled,
        int containerMaxDepth,
        boolean scanInnerItems,
        boolean customMetadataEnabled,
        boolean flagCustomName,
        boolean flagLore,
        boolean flagCustomModelData
) {
    public boolean ignoresNamespace(String namespace) {
        return namespace != null && ignoredNamespaces.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static ScannerSettings defaults() {
        return new ScannerSettings(
                true,
                false,
                Set.of("minecraft", "bukkit", "paper", "itemsadder", "oraxen", "mmoitems", "mythicmobs", "executableitems"),
                true,
                true,
                true,
                "SUSPICIOUS",
                true,
                true,
                true,
                50.0,
                Set.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1"),
                Set.of(),
                Set.of("itemsadder", "oraxen", "mmoitems"),
                "SUSPICIOUS",
                true,
                true,
                true,
                true,
                true,
                Set.of("MAX_STACK_SIZE", "ATTRIBUTE_MODIFIERS", "MAX_DAMAGE", "ENCHANTMENTS"),
                Set.of("CUSTOM_MODEL_DATA", "ITEM_NAME", "CUSTOM_NAME", "LORE", "FOOD", "CONSUMABLE", "EQUIPPABLE", "TOOL", "RARITY", "POTION_CONTENTS", "CONTAINER", "BUNDLE_CONTENTS"),
                true,
                Set.of(),
                Set.of("minecraft", "bukkit", "paper", "husksync", "itemsadder", "oraxen", "mmoitems"),
                true,
                3,
                true,
                true,
                true,
                true,
                true
        );
    }
}
