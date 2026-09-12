package com.npucraft.itemguard.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Material-only nested counts. Does not serialize or hash inner stacks.
 * Prefers a single Paper view of contents so BlockStateMeta and Data Components are not double-counted.
 */
public final class NestedContents {

    private NestedContents() {
    }

    public static boolean isContainerItem(ItemStack stack) {
        return Items.isShulkerBox(stack) || isBundle(stack);
    }

    public static boolean isBundle(ItemStack stack) {
        return !Items.isEmpty(stack) && stack.getType().name().contains("BUNDLE");
    }

    public static Map<String, Integer> materialTotals(ItemStack stack, int maxDepth) {
        Map<String, Integer> totals = new HashMap<>();
        collect(stack, 0, maxDepth, totals);
        return Map.copyOf(totals);
    }

    private static void collect(ItemStack stack, int depth, int maxDepth, Map<String, Integer> totals) {
        if (Items.isEmpty(stack) || depth >= maxDepth) {
            return;
        }
        for (ItemStack inner : children(stack)) {
            if (Items.isEmpty(inner)) {
                continue;
            }
            totals.merge(inner.getType().name(), inner.getAmount(), Integer::sum);
            collect(inner, depth + 1, maxDepth, totals);
        }
    }

    public static List<ItemStack> children(ItemStack stack) {
        if (Items.isEmpty(stack)) {
            return List.of();
        }
        ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null && !container.contents().isEmpty()) {
            return copyNonEmpty(container.contents());
        }
        BundleContents bundleContents = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundleContents != null && !bundleContents.contents().isEmpty()) {
            return copyNonEmpty(bundleContents.contents());
        }
        if (stack.getItemMeta() instanceof BundleMeta bundle && bundle.hasItems()) {
            return copyNonEmpty(bundle.getItems());
        }
        if (stack.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()) {
            BlockState state = meta.getBlockState();
            if (state instanceof Container blockContainer) {
                return copyNonEmpty(List.of(blockContainer.getInventory().getContents()));
            }
        }
        return List.of();
    }

    private static List<ItemStack> copyNonEmpty(Iterable<ItemStack> contents) {
        List<ItemStack> inner = new ArrayList<>();
        for (ItemStack content : contents) {
            if (!Items.isEmpty(content)) {
                inner.add(content);
            }
        }
        return inner;
    }
}
