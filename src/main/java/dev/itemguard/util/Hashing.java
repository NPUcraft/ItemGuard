package dev.itemguard.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic SHA-256 helper. A new digest is created per call so it is thread-safe.
 */
public final class Hashing {

    private static final HexFormat HEX = HexFormat.of();

    private Hashing() {
    }

    public static String sha256Hex(byte[] data) {
        Objects.requireNonNull(data, "data");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but missing from this JVM", exception);
        }
    }
}
