import java.security.MessageDigest
import java.util.jar.JarFile

plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

description = "Item security, item-flow monitoring and economy anomaly detection for Paper servers."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.william278.net/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("net.william278.husksync:husksync-bukkit:3.8.7+1.21.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    archiveBaseName.set("ItemGuard")
    archiveVersion.set(project.version.toString())
    from(project.projectDir) {
        include("README.md")
        into("META-INF")
    }
}

tasks {
    runServer {
        minecraftVersion("1.21.8")
        runDirectory.set(file("run"))
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf(
        "version" to project.version.toString(),
        "description" to project.description
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.register<Exec>("integrationTest") {
    group = "verification"
    description = "Real Paper 1.21.8 + Mineflayer integration tests. Not part of the normal build."
    dependsOn("jar", ":harness:jar")
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "integration-tests/bot/runner.js")
    val paperJarPath = (findProperty("paperJar") as String?)
        ?: System.getenv("ITEMGUARD_PAPER_JAR")
        ?: file("run/paper-1.21.8.jar").absolutePath
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    environment("ITEMGUARD_REPORTS_DIR", file("integration-tests/reports").absolutePath)
    environment("ITEMGUARD_RUNTIME_DIR", layout.buildDirectory.dir("integration-runtime").get().asFile.absolutePath)
    environment("ITEMGUARD_PAPER_JAR", paperJarPath)
    doFirst {
        environment(
            "ITEMGUARD_ITEMGUARD_JAR",
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        )
        environment(
            "ITEMGUARD_HARNESS_JAR",
            file("integration-tests/harness/build/libs/ItemGuard-TestHarness-1.0.0-test.jar").absolutePath
        )
    }
}

tasks.register<Exec>("huskSyncIntegrationTest") {
    group = "verification"
    description = "Velocity + Paper A/B + HuskSync 3.8.7 + MariaDB + Redis + Mineflayer. Not part of build or integrationTest."
    dependsOn("jar", ":harness:jar")
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "integration-tests/husksync/runner.js")
    val paperJarPath = (findProperty("paperJar") as String?)
        ?: System.getenv("ITEMGUARD_PAPER_JAR")
        ?: file("run/paper-1.21.8.jar").absolutePath
    val huskSyncJarPath = (findProperty("HuskSyncJar") as String?)
        ?: System.getenv("ITEMGUARD_HUSKSYNC_JAR")
    val velocityJarPath = (findProperty("VelocityJar") as String?)
        ?: System.getenv("ITEMGUARD_VELOCITY_JAR")
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    environment("ITEMGUARD_REPORTS_DIR", file("integration-tests/reports/husksync").absolutePath)
    environment("ITEMGUARD_RUNTIME_DIR", layout.buildDirectory.dir("husksync-integration").get().asFile.absolutePath)
    environment("ITEMGUARD_PAPER_JAR", paperJarPath)
    if (!huskSyncJarPath.isNullOrBlank()) {
        environment("ITEMGUARD_HUSKSYNC_JAR", huskSyncJarPath)
    }
    if (!velocityJarPath.isNullOrBlank()) {
        environment("ITEMGUARD_VELOCITY_JAR", velocityJarPath)
    }
    doFirst {
        environment(
            "ITEMGUARD_ITEMGUARD_JAR",
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        )
        environment(
            "ITEMGUARD_HARNESS_JAR",
            file("integration-tests/harness/build/libs/ItemGuard-TestHarness-1.0.0-test.jar").absolutePath
        )
    }
}

tasks.register("fullIntegrationTest") {
    group = "verification"
    description = "Unit tests, single-server runtime tests, then HuskSync cluster tests."
    dependsOn("test", "integrationTest", "huskSyncIntegrationTest")
}

tasks.named("integrationTest") {
    mustRunAfter("test")
}

tasks.named("huskSyncIntegrationTest") {
    mustRunAfter("integrationTest")
}

tasks.register<Exec>("upgradeIntegrationTest") {
    group = "verification"
    description = "Real Paper RC1 → current candidate upgrade. Not part of build or check."
    dependsOn("jar", ":harness:jar")
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "integration-tests/upgrade/runner.js")
    val paperJarPath = (findProperty("paperJar") as String?)
        ?: System.getenv("ITEMGUARD_PAPER_JAR")
        ?: file("run/paper-1.21.8.jar").absolutePath
    val rc1JarPath = (findProperty("Rc1Jar") as String?)
        ?: System.getenv("ITEMGUARD_RC1_JAR")
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    environment("ITEMGUARD_REPORTS_DIR", file("integration-tests/reports/upgrade").absolutePath)
    environment("ITEMGUARD_RUNTIME_DIR", layout.buildDirectory.dir("upgrade-runtime").get().asFile.absolutePath)
    environment("ITEMGUARD_PAPER_JAR", paperJarPath)
    environment("ITEMGUARD_EXPECT_VERSION", project.version.toString())
    if (!rc1JarPath.isNullOrBlank()) {
        environment("ITEMGUARD_RC1_JAR", rc1JarPath)
    }
    doFirst {
        environment(
            "ITEMGUARD_ITEMGUARD_JAR",
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        )
        environment(
            "ITEMGUARD_HARNESS_JAR",
            file("integration-tests/harness/build/libs/ItemGuard-TestHarness-1.0.0-test.jar").absolutePath
        )
    }
}

tasks.register<Exec>("legacyConfigCompatibilityTest") {
    group = "verification"
    description = "Paper RC1 YAML fixture + RC2. Not exact artifact upgrade. Not part of build."
    dependsOn("jar", ":harness:jar")
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "integration-tests/legacy-config/runner.js")
    val paperJarPath = (findProperty("paperJar") as String?)
        ?: System.getenv("ITEMGUARD_PAPER_JAR")
        ?: file("run/paper-1.21.8.jar").absolutePath
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    environment("ITEMGUARD_REPORTS_DIR", file("integration-tests/reports/legacy-config").absolutePath)
    environment("ITEMGUARD_RUNTIME_DIR", layout.buildDirectory.dir("legacy-config-runtime").get().asFile.absolutePath)
    environment("ITEMGUARD_PAPER_JAR", paperJarPath)
    environment("ITEMGUARD_EXPECT_VERSION", project.version.toString())
    doFirst {
        environment(
            "ITEMGUARD_ITEMGUARD_JAR",
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        )
        environment(
            "ITEMGUARD_HARNESS_JAR",
            file("integration-tests/harness/build/libs/ItemGuard-TestHarness-1.0.0-test.jar").absolutePath
        )
    }
}

tasks.register<Exec>("fieldLogAnalysis") {
    group = "verification"
    description = "Parse field forensic ZIP/JSONL into analysis/. Not part of build. Dev-only."
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "tools/field-log-analyzer/analyze.js")
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    val zipPath = (findProperty("FieldLogZip") as String?)
        ?: System.getenv("ITEMGUARD_FIELD_LOG_ZIP")
        ?: file("itemguard-log.zip").absolutePath
    environment("ITEMGUARD_FIELD_LOG_ZIP", zipPath)
    val dirPath = (findProperty("FieldLogDir") as String?)
        ?: System.getenv("ITEMGUARD_FIELD_LOG_DIR")
    if (!dirPath.isNullOrBlank()) {
        environment("ITEMGUARD_FIELD_LOG_DIR", dirPath)
    }
}

tasks.register<Exec>("fieldValidationCompare") {
    group = "verification"
    description = "Compare PRE-HARDENING baseline report vs a NEW post-hardening forensic zip. Never overwrites the baseline zip/report. Dev-only."
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "tools/field-log-analyzer/compare.js")
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    val zipPath = (findProperty("FieldLogZip") as String?)
        ?: System.getenv("ITEMGUARD_FIELD_AFTER_ZIP")
        ?: System.getenv("ITEMGUARD_FIELD_LOG_ZIP")
    if (!zipPath.isNullOrBlank()) {
        environment("ITEMGUARD_FIELD_AFTER_ZIP", zipPath)
        environment("ITEMGUARD_FIELD_LOG_ZIP", zipPath)
    }
    val dirPath = (findProperty("FieldLogDir") as String?)
        ?: System.getenv("ITEMGUARD_FIELD_AFTER_DIR")
        ?: System.getenv("ITEMGUARD_FIELD_LOG_DIR")
    if (!dirPath.isNullOrBlank()) {
        environment("ITEMGUARD_FIELD_AFTER_DIR", dirPath)
        environment("ITEMGUARD_FIELD_LOG_DIR", dirPath)
    }
}

tasks.register<Exec>("releaseSmokeTest") {
    group = "verification"
    description = "Clean Paper install smoke: bundled configs, no HuskSync, /ig status, restart. Not part of build."
    dependsOn("jar")
    workingDir = projectDir
    outputs.upToDateWhen { false }
    commandLine("node", "integration-tests/smoke/runner.js")
    val paperJarPath = (findProperty("paperJar") as String?)
        ?: System.getenv("ITEMGUARD_PAPER_JAR")
        ?: file("run/paper-1.21.8.jar").absolutePath
    environment("ITEMGUARD_ROOT", projectDir.absolutePath)
    environment("ITEMGUARD_REPORTS_DIR", file("integration-tests/reports/smoke").absolutePath)
    environment("ITEMGUARD_RUNTIME_DIR", layout.buildDirectory.dir("release-smoke").get().asFile.absolutePath)
    environment("ITEMGUARD_PAPER_JAR", paperJarPath)
    doFirst {
        environment(
            "ITEMGUARD_ITEMGUARD_JAR",
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        )
    }
}

tasks.register("packageRelease") {
    group = "distribution"
    description = "Copy ItemGuard JAR, SHA-256, changelog and release notes into build/release/"
    dependsOn("jar")
    doLast {
        val version = project.version.toString()
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val releaseDir = layout.buildDirectory.dir("release").get().asFile
        releaseDir.mkdirs()
        val named = file("$releaseDir/ItemGuard-$version.jar")
        jarFile.copyTo(named, overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256").digest(named.readBytes())
        val hash = digest.joinToString("") { byte: Byte -> "%02x".format(byte.toInt() and 0xFF) }
        file("$releaseDir/ItemGuard-$version.sha256").writeText("$hash  ItemGuard-$version.jar\n")

        val changelog = file("CHANGELOG.md")
        if (changelog.exists()) {
            changelog.copyTo(file("$releaseDir/CHANGELOG.md"), overwrite = true)
        }
        val notes = file("RELEASE-NOTES-$version.md")
        if (notes.exists()) {
            notes.copyTo(file("$releaseDir/${notes.name}"), overwrite = true)
        }
        val readme = file("README.md")
        if (readme.exists()) {
            readme.copyTo(file("$releaseDir/README.md"), overwrite = true)
        }

        JarFile(named).use { jar: JarFile ->
            val listing = jar.entries().asSequence().map { entry -> entry.name }.toList()
            val forbidden = listing.filter { entry: String ->
                entry.startsWith("integration-tests/")
                    || entry.contains("node_modules/")
                    || entry.contains("mineflayer", ignoreCase = true)
                    || entry.contains("docker-compose", ignoreCase = true)
                    || entry.contains("ItemGuard-TestHarness")
                    || entry.contains("HuskSync", ignoreCase = true) && entry.endsWith(".jar")
                    || entry.contains("velocity", ignoreCase = true) && entry.endsWith(".jar")
                    || entry.endsWith("paper-1.21.8.jar")
                    || entry.contains("reports/")
                    || entry.endsWith(".jsonl")
            }
            if (forbidden.isNotEmpty()) {
                error("Release JAR contains forbidden entries: $forbidden")
            }
            if (listing.none { name: String -> name == "plugin.yml" }) {
                error("Release JAR is missing plugin.yml")
            }
            val required = listOf(
                "config.yml",
                "scanner.yml",
                "risk.yml",
                "items.yml",
                "alerts.yml",
                "integrations.yml",
                "messages.yml",
                "logging.yml",
                "com/npucraft/itemguard/log/ForensicLogService.class",
                "com/npucraft/itemguard/log/JsonlLogWriter.class",
                "com/npucraft/itemguard/log/ForensicLogPublisher.class"
            )
            val missing = required.filter { name: String -> listing.none { entry: String -> entry == name } }
            if (missing.isNotEmpty()) {
                error("Release JAR is missing required entries: $missing")
            }
            val pluginYml = jar.getInputStream(jar.getJarEntry("plugin.yml")).bufferedReader().readText()
            if (!pluginYml.contains("version: $version") && !pluginYml.contains("version: '$version'")) {
                error("plugin.yml inside the JAR does not declare version $version")
            }
            if (pluginYml.lineSequence().any { line: String -> line.trim().startsWith("depend:") }) {
                error("plugin.yml must softdepend HuskSync, not depend on it")
            }
        }
        logger.lifecycle("Packed ${named.name}")
        logger.lifecycle("SHA-256: $hash")
        writeReleaseManifestFile(releaseDir, version, hash, named.name)
    }
}

tasks.register("writeReleaseManifest") {
    group = "distribution"
    description = "Write build/release/RELEASE-MANIFEST.json without packaging a release JAR."
    dependsOn("jar")
    doLast {
        val version = project.version.toString()
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val releaseDir = layout.buildDirectory.dir("release").get().asFile
        releaseDir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256").digest(jarFile.readBytes())
        val hash = digest.joinToString("") { byte: Byte -> "%02x".format(byte.toInt() and 0xFF) }
        writeReleaseManifestFile(releaseDir, version, hash, jarFile.name)
        logger.lifecycle("Wrote ${file("$releaseDir/RELEASE-MANIFEST.json")}")
        logger.lifecycle("SHA-256: $hash")
    }
}

fun writeReleaseManifestFile(releaseDir: File, version: String, hash: String, artifactName: String) {
    val committed = readVerificationStatusFile()
    val upgradeReport = file("integration-tests/reports/upgrade/results.json")
    val legacyReport = file("integration-tests/reports/legacy-config/results.json")
    val upgradeStatus = readJsonStatus(upgradeReport) ?: committed["exactArtifactUpgrade"]
    val legacyStatus = readJsonStatus(legacyReport) ?: committed["configCompatibilityUpgrade"]
    val verification = when {
        upgradeStatus == "PASS" -> "EXACT"
        committed["upgradeVerification"] != null && upgradeStatus == null && legacyStatus == null ->
            committed.getValue("upgradeVerification")
        legacyStatus == "PASS" -> "CONFIG_ONLY"
        else -> committed["upgradeVerification"] ?: "BLOCKED"
    }
    val countedTests = countUnitTests()
    val unitTests = if (countedTests >= 0) {
        countedTests
    } else {
        committed["unitTests"]?.toIntOrNull() ?: -1
    }
    val json = buildString {
        appendLine("{")
        appendLine("  \"version\": ${jsonQuote(version)},")
        appendLine("  \"artifact\": ${jsonQuote(artifactName)},")
        appendLine("  \"sha256\": ${jsonQuote(hash)},")
        appendLine("  \"gitCommit\": ${jsonQuote(resolveGitCommit())},")
        appendLine("  \"java\": \"21\",")
        appendLine("  \"paperTarget\": \"1.21.8\",")
        appendLine("  \"gradle\": \"8.14\",")
        appendLine("  \"unitTests\": $unitTests,")
        appendLine("  \"singleServerIntegration\": ${jsonQuote(singleServerManifestStatus(committed))},")
        appendLine("  \"huskSyncIntegration\": ${jsonQuote(huskSyncManifestStatus(committed))},")
        appendLine("  \"exactArtifactUpgrade\": ${jsonQuote(upgradeStatus ?: "NOT_RUN")},")
        appendLine("  \"configCompatibilityUpgrade\": ${jsonQuote(legacyStatus ?: "NOT_RUN")},")
        appendLine("  \"upgradeVerification\": ${jsonQuote(verification)}")
        appendLine("}")
    }
    file("$releaseDir/RELEASE-MANIFEST.json").writeText(json)
}

fun jsonQuote(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun resolveGitCommit(): String {
    val fromEnv = System.getenv("GITHUB_SHA")?.trim().orEmpty()
    if (fromEnv.matches(Regex("[0-9a-f]{7,40}"))) {
        return fromEnv
    }
    return try {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && text.matches(Regex("[0-9a-f]{7,40}"))) {
            text
        } else {
            "unavailable"
        }
    } catch (_: Exception) {
        "unavailable"
    }
}

fun readVerificationStatusFile(): Map<String, String> {
    val file = file("integration-tests/artifacts/verification-status.json")
    if (!file.isFile) {
        return emptyMap()
    }
    val text = file.readText()
    fun field(name: String): String? {
        val match = Regex("\"" + name + "\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|\\d+)").find(text) ?: return null
        val raw = match.groupValues[1]
        return if (raw.startsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }
    return listOf(
        "exactArtifactUpgrade",
        "configCompatibilityUpgrade",
        "upgradeVerification",
        "unitTests",
        "singleServerIntegration",
        "huskSyncIntegration"
    ).mapNotNull { key: String -> field(key)?.let { value: String -> key to value } }.toMap()
}

fun readJsonStatus(file: File): String? {
    if (!file.isFile) {
        return null
    }
    val text = file.readText()
    val match = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(text) ?: return null
    return match.groupValues[1]
}

fun countUnitTests(): Int {
    val dir = file("build/test-results/test")
    if (!dir.isDirectory) {
        return -1
    }
    return dir.listFiles { candidate: File -> candidate.name.startsWith("TEST-") && candidate.name.endsWith(".xml") }
        ?.sumOf { xml: File ->
            val match = Regex("""tests="(\d+)"""").find(xml.readText())
            match?.groupValues?.get(1)?.toInt() ?: 0
        } ?: -1
}

fun readMarkdownPassLine(path: String, fallback: String): String {
    val file = file(path)
    if (!file.isFile) {
        return "NOT_RUN"
    }
    val text = file.readText()
    val pass = Regex("""PASS (\d+) / FAIL (\d+)""").find(text)
    return if (pass != null) {
        "PASS ${pass.groupValues[1]} / FAIL ${pass.groupValues[2]}"
    } else {
        fallback
    }
}

fun singleServerManifestStatus(committed: Map<String, String> = emptyMap()): String {
    val report = file("integration-tests/reports/results.md")
    if (!report.isFile) {
        return committed["singleServerIntegration"] ?: "NOT_RUN"
    }
    val summary = readMarkdownPassLine("integration-tests/reports/results.md", "NOT_RUN")
    return if (summary == "PASS 14 / FAIL 0") {
        "14/14 PASS"
    } else {
        summary
    }
}

fun huskSyncManifestStatus(committed: Map<String, String> = emptyMap()): String {
    val file = file("integration-tests/reports/husksync/results.md")
    if (!file.isFile) {
        return committed["huskSyncIntegration"] ?: "NOT_RUN"
    }
    val text = file.readText()
    val hsPass = (1..5).all { index: Int -> text.contains("PASS HS-00$index") }
    return if (hsPass) {
        "HS-001..HS-005 PASS"
    } else {
        readMarkdownPassLine("integration-tests/reports/husksync/results.md", "HS-001..HS-005")
    }
}
