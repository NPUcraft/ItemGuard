package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.InventorySnapshot;
import com.npucraft.itemguard.flow.model.SlotSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts snapshot diffs into economy flow gains.
 * Newly acquired container items contribute +1 container, not their inner stacks.
 * Moving a shulker between slots is a zero combined delta.
 */
public final class InventoryEconomics {

    private InventoryEconomics() {
    }

    public static Map<String, Integer> flowGains(InventorySnapshot before, InventorySnapshot after) {
        Map<String, Integer> combinedBefore = sum(before.materialTotals(), before.nestedTotals());
        Map<String, Integer> combinedAfter = sum(after.materialTotals(), after.nestedTotals());
        Map<String, Integer> combinedGains = MaterialDiff.positive(MaterialDiff.subtract(combinedAfter, combinedBefore));
        Map<String, Integer> acquiredNested = acquiredContainerNested(before, after);
        Map<String, Integer> flow = new HashMap<>();
        combinedGains.forEach((material, amount) -> {
            int adjusted = amount - acquiredNested.getOrDefault(material, 0);
            if (adjusted > 0) {
                flow.put(material, adjusted);
            }
        });
        return Map.copyOf(flow);
    }

    public static Map<String, Integer> acquiredContainerNested(InventorySnapshot before, InventorySnapshot after) {
        Map<String, Integer> beforeCounts = containerCounts(before);
        Map<String, Integer> afterCounts = containerCounts(after);
        Map<String, Map<String, Integer>> nestedBySignature = new HashMap<>();
        for (SlotSnapshot slot : after.slots()) {
            if (slot.empty() || !slot.container()) {
                continue;
            }
            nestedBySignature.putIfAbsent(slot.signatureKey(), slot.nestedMaterials());
        }
        Map<String, Integer> acquired = new HashMap<>();
        afterCounts.forEach((signature, afterCount) -> {
            int extra = afterCount - beforeCounts.getOrDefault(signature, 0);
            if (extra <= 0) {
                return;
            }
            Map<String, Integer> nested = nestedBySignature.getOrDefault(signature, Map.of());
            for (int i = 0; i < extra; i++) {
                nested.forEach((material, amount) -> acquired.merge(material, amount, Integer::sum));
            }
        });
        return acquired;
    }

    public static int nestedDepthGuard(int depth, int maxDepth) {
        return Math.max(0, Math.min(depth, Math.max(0, maxDepth)));
    }

    public static boolean canRecurse(int depth, int maxDepth) {
        return depth < Math.max(0, maxDepth);
    }

    private static Map<String, Integer> containerCounts(InventorySnapshot snapshot) {
        Map<String, Integer> counts = new HashMap<>();
        for (SlotSnapshot slot : snapshot.slots()) {
            if (slot.empty() || !slot.container()) {
                continue;
            }
            counts.merge(slot.signatureKey(), Math.max(1, slot.amount()), Integer::sum);
        }
        return counts;
    }

    private static Map<String, Integer> sum(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> total = new HashMap<>(left);
        right.forEach((key, amount) -> total.merge(key, amount, Integer::sum));
        return total;
    }
}
