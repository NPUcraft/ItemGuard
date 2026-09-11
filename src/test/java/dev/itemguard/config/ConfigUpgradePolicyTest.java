package dev.itemguard.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpgradePolicyTest {

    private static final List<String> OLD_YAML = List.of(
            "config.yml",
            "scanner.yml",
            "risk.yml",
            "items.yml",
            "alerts.yml",
            "integrations.yml",
            "messages.yml"
    );

    @TempDir
    Path temp;

    @Test
    void missingLoggingYmlIsInstalledWithoutRewritingOldYaml() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "debug: true\ntrace-retention-custom: 47\n");
        for (String name : ConfigResourcePolicy.BUNDLED_FILES) {
            File destination = temp.resolve(name).toFile();
            if (ConfigResourcePolicy.shouldInstallBundledResource(destination)) {
                Files.writeString(destination.toPath(), "BUNDLED-" + name + "\n");
            }
        }
        assertEquals("debug: true\ntrace-retention-custom: 47\n", Files.readString(temp.resolve("config.yml")));
        assertEquals("BUNDLED-logging.yml\n", Files.readString(temp.resolve("logging.yml")));
        assertTrue(ConfigResourcePolicy.BUNDLED_FILES.contains("logging.yml"));
        assertEquals(8, ConfigResourcePolicy.BUNDLED_FILES.size());
    }

    @Test
    void existingLoggingYmlIsPreserved() throws Exception {
        Files.writeString(temp.resolve("logging.yml"), "logging:\n  server-name: already-here\n");
        assertFalse(ConfigResourcePolicy.shouldInstallBundledResource(temp.resolve("logging.yml").toFile()));
        for (String name : ConfigResourcePolicy.BUNDLED_FILES) {
            File destination = temp.resolve(name).toFile();
            if (ConfigResourcePolicy.shouldInstallBundledResource(destination)) {
                Files.writeString(destination.toPath(), "BUNDLED-" + name + "\n");
            }
        }
        assertEquals("logging:\n  server-name: already-here\n", Files.readString(temp.resolve("logging.yml")));
    }

    @Test
    void existingOldYamlIsNeverTreatedAsMissing() throws Exception {
        for (String name : OLD_YAML) {
            Files.writeString(temp.resolve(name), "CUSTOM-" + name + "\n");
            assertFalse(ConfigResourcePolicy.shouldInstallBundledResource(temp.resolve(name).toFile()));
        }
        assertTrue(ConfigResourcePolicy.shouldInstallBundledResource(temp.resolve("logging.yml").toFile()));
        assertFalse(ConfigResourcePolicy.shouldInstallBundledResource(null));
        assertTrue(ConfigResourcePolicy.shouldInstallBundledResource(temp.resolve("does-not-exist.yml").toFile()));
    }

    @Test
    void missingKeyUsesInMemoryDefaultWithoutRewritingTheFile() throws Exception {
        Path file = temp.resolve("config.yml");
        String original = "debug: true\n";
        Files.writeString(file, original);
        assertFalse(original.contains("retention-minutes"));
        assertEquals(30, PluginSettings.defaults().traceRetention().toMinutes());
        assertEquals(60, AlertSettings.defaults().threshold());
        assertEquals(80, AlertSettings.defaults().criticalThreshold());
        assertEquals(original, Files.readString(file));
    }
}
