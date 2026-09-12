package com.npucraft.itemguard.item;

import org.bukkit.inventory.ItemStack;

public final class Items {

    private Items() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    public static String materialName(ItemStack stack) {
        return isEmpty(stack) ? "AIR" : stack.getType().name();
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return !isEmpty(stack) && stack.getType().name().endsWith("SHULKER_BOX");
    }

    /**
     * Live stack comparison that ignores amount. Prefer this over hashing when only identity is needed.
     */
    public static boolean similar(ItemStack left, ItemStack right) {
        if (isEmpty(left) && isEmpty(right)) {
            return true;
        }
        if (isEmpty(left) || isEmpty(right)) {
            return false;
        }
        return left.isSimilar(right);
    }
}
