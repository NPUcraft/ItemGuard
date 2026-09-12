package com.npucraft.itemguard.item;

import com.npucraft.itemguard.config.PluginSettings;
import com.npucraft.itemguard.util.Hashing;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemSignatures {

    private final Logger logger;

    public ItemSignatures(Logger logger) {
        this.logger = logger;
    }

    public ItemSignature of(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return ItemSignature.materialOnly("AIR");
        }
        String material = stack.getType().name();
        try {
            ItemStack clone = stack.clone();
            clone.setAmount(1);
            return new ItemSignature(material, Hashing.sha256Hex(clone.serializeAsBytes()));
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Failed to hash item signature for " + material, exception);
            return new ItemSignature(material, "fallback:" + material);
        }
    }

    public boolean shouldHash(ItemStack stack, int valueWeight, PluginSettings settings) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (valueWeight >= settings.signatureMinValue()) {
            return true;
        }
        if (!settings.hashCustomItems()) {
            return false;
        }
        return stack.hasItemMeta();
    }

    /**
     * Fast identity check for live stacks. SHA-256 signatures are for stored keys, not per-tick comparison.
     */
    public static boolean sameIgnoringAmount(ItemStack left, ItemStack right) {
        return Items.similar(left, right);
    }

    public static boolean looksCustom(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        var meta = stack.getItemMeta();
        return meta != null && (meta.hasDisplayName()
                || meta.hasLore()
                || stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)
                || meta.hasEnchants()
                || meta.hasAttributeModifiers()
                || meta instanceof BlockStateMeta
                || meta instanceof BundleMeta);
    }
}
