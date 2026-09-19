package com.npucraft.itemguard.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginManifestTest {

    private static final List<String> PERMISSIONS = List.of(
            "itemguard.admin",
            "itemguard.status",
            "itemguard.inspect",
            "itemguard.trace",
            "itemguard.scan",
            "itemguard.alerts",
            "itemguard.reload",
            "itemguard.debug"
    );

    @Test
    void processedPluginYmlMatchesReleaseContract() throws IOException {
        String yaml = readClasspath("/plugin.yml");
        assertFalse(yaml.contains("${version}"), "plugin.yml version must be expanded by Gradle");
        assertFalse(yaml.contains("${description}"), "plugin.yml description must be expanded by Gradle");
        assertTrue(yaml.contains("name: ItemGuard"));
        String version = gradleVersion();
        assertTrue(
                yaml.contains("version: " + version) || yaml.contains("version: '" + version + "'"),
                "plugin.yml version must match gradle.properties (" + version + ")"
        );
        assertTrue(yaml.contains("main: com.npucraft.itemguard.ItemGuardPlugin"));
        assertTrue(yaml.contains("author: NPUcraft"));
        assertTrue(yaml.contains("api-version: '1.21.8'") || yaml.contains("api-version: 1.21.8"));
        assertTrue(yaml.contains("softdepend:"));
        assertTrue(yaml.contains("HuskSync"));
        assertFalse(Pattern.compile("(?m)^depend:\\s*$").matcher(yaml).find(), "HuskSync must be softdepend, not depend");
        for (String permission : PERMISSIONS) {
            assertTrue(yaml.contains(permission + ":"), "missing permission " + permission);
        }
        assertTrue(yaml.contains("itemguard:"));
        assertTrue(yaml.contains("[ig]"));
    }

    @Test
    void bundledConfigsAreUtf8AndHaveNoPlaceholders() throws IOException {
        for (String name : List.of(
                "config.yml",
                "scanner.yml",
                "risk.yml",
                "items.yml",
                "alerts.yml",
                "integrations.yml",
                "messages.yml",
                "logging.yml"
        )) {
            String text = readClasspath("/" + name);
            assertFalse(text.contains("${"), name + " still contains a Gradle placeholder");
            assertFalse(text.contains("TODO"), name + " contains TODO");
            assertFalse(text.contains("FIXME"), name + " contains FIXME");
            assertFalse(text.toLowerCase().contains("itemguardbot"), name + " looks like a test path");
        }
        String items = readClasspath("/items.yml");
        for (String material : List.of(
                "DIAMOND:",
                "DIAMOND_CHESTPLATE:",
                "NETHERITE_CHESTPLATE:",
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE:",
                "DIAMOND_AXE:",
                "DIAMOND_BLOCK:",
                "NETHERITE_SCRAP:",
                "NETHERITE_INGOT:",
                "NETHERITE_BLOCK:",
                "ELYTRA:",
                "TOTEM_OF_UNDYING:",
                "ENCHANTED_GOLDEN_APPLE:",
                "NETHER_STAR:",
                "BEACON:",
                "DRAGON_EGG:",
                "HEAVY_CORE:",
                "MACE:",
                "TRIAL_KEY:",
                "OMINOUS_TRIAL_KEY:"
        )) {
            assertTrue(items.contains(material), "items.yml missing " + material);
        }
        String scanner = readClasspath("/scanner.yml");
        assertTrue(scanner.contains("bukkit"));
        assertTrue(scanner.contains("paper"));
        assertTrue(scanner.contains("denied-namespaces: []"));
        String risk = readClasspath("/risk.yml");
        assertTrue(containsScore(risk, "UNEXPLAINED_ITEM_GAIN", 35));
        assertTrue(containsScore(risk, "HIGH_VALUE_ITEM_BURST", 20));
        assertTrue(containsScore(risk, "HUSKSYNC_DATA_APPLY", 0));
        assertTrue(containsScore(risk, "OVERSIZED_STACK", 45));
        assertTrue(risk.contains("high: 60"));
        assertTrue(risk.contains("critical: 80"));
        String integrations = readClasspath("/integrations.yml");
        assertTrue(integrations.contains("enabled: true"));
        String config = readClasspath("/config.yml");
        assertTrue(config.contains("debug: false"));
        String logging = readClasspath("/logging.yml");
        assertTrue(logging.contains("format: JSONL"));
        assertTrue(logging.contains("retention-days: 30"));
        assertTrue(logging.contains("directory: logs"));
        assertTrue(logging.contains("main, industrial, resource"));
        assertTrue(config.contains("heartbeat-interval-ticks: 40"));
        assertTrue(config.contains("scan-debounce-millis: 1500"));
        String messages = readClasspath("/messages.yml");
        assertFalse(hasDuplicateTopLevelKeys(messages), "messages.yml has duplicate keys");
        assertFalse(messages.contains("Steve"));
    }

    @Test
    void scannerDefaultsIgnoreVanillaPlatformNamespaces() {
        var defaults = com.npucraft.itemguard.config.ScannerSettings.defaults();
        assertTrue(defaults.ignoredPdcNamespaces().contains("minecraft"));
        assertTrue(defaults.ignoredPdcNamespaces().contains("bukkit"));
        assertTrue(defaults.ignoredPdcNamespaces().contains("paper"));
        assertTrue(defaults.deniedPdcNamespaces().isEmpty());
    }

    private static boolean containsScore(String yaml, String key, int value) {
        Matcher matcher = Pattern.compile(key + ":\\s*" + value + "\\b").matcher(yaml);
        return matcher.find();
    }

    private static boolean hasDuplicateTopLevelKeys(String yaml) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : yaml.split("\\R")) {
            if (line.startsWith(" ") || line.startsWith("\t") || line.startsWith("#") || line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon);
            if (!seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    private static String gradleVersion() throws IOException {
        for (String line : Files.readString(Path.of("gradle.properties")).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("version=")) {
                return trimmed.substring("version=".length()).trim();
            }
        }
        throw new IOException("version= missing from gradle.properties");
    }

    private static String readClasspath(String path) throws IOException {
        try (InputStream in = PluginManifestTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing classpath resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
