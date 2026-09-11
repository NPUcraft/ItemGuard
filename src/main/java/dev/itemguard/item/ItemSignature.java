package dev.itemguard.item;

import java.util.Objects;

/**
 * Amount-independent identity of an item. Two stacks of the same item share a signature
 * regardless of stack size.
 */
public record ItemSignature(String material, String hash) {

    public ItemSignature {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(hash, "hash");
    }

    public static ItemSignature materialOnly(String material) {
        return new ItemSignature(material, "material-only");
    }

    public boolean isDetailed() {
        return !"material-only".equals(hash) && !hash.startsWith("fallback:");
    }

    public String compact() {
        if (!isDetailed()) {
            return material;
        }
        String shortHash = hash.length() <= 12 ? hash : hash.substring(0, 12);
        return material + "#" + shortHash;
    }
}
