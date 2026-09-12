plugins {
    java
}

group = "com.npucraft.itemguard"
version = "1.0.0-test"
description = "Integration-only Paper plugin. Not part of the ItemGuard product JAR."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.william278.net/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly(rootProject)
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("net.william278.husksync:husksync-bukkit:3.8.7+1.21.8")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    archiveBaseName.set("ItemGuard-TestHarness")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
}
