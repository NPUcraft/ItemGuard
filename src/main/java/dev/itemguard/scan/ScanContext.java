package dev.itemguard.scan;

import dev.itemguard.config.ScannerSettings;
import dev.itemguard.item.ItemSignature;

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
