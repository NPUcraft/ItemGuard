package com.npucraft.itemguard.config;

import java.io.File;
import java.util.List;

/**
 * Bundled YAML install rules. Existing operator files are never replaced.
 */
public final class ConfigResourcePolicy {

    public static final List<String> BUNDLED_FILES = List.of(
            "config.yml",
            "scanner.yml",
            "risk.yml",
            "items.yml",
            "alerts.yml",
            "integrations.yml",
            "messages.yml",
            "logging.yml"
    );

    private ConfigResourcePolicy() {
    }

    /**
     * Existing operator files are never replaced. Only a missing file is copied from the JAR.
     */
    public static boolean shouldInstallBundledResource(File destination) {
        return destination != null && !destination.exists();
    }
}
