package com.npucraft.itemguard.scan;

import com.npucraft.itemguard.config.ScannerSettings;
import com.npucraft.itemguard.item.ItemSignature;

public record ScanContext(
        ScannerSettings settings,
        ItemSignature signature,
        int depth,
        int maxDepth
) {
    public ScanContext deeper() {
        return new ScanContext(settings, signature, depth + 1, maxDepth);
    }

    public boolean canRecurse() {
        return depth < maxDepth;
    }
}
