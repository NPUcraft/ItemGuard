plugins {
    java
}

group = "com.npucraft.itemguard"
version = "1.0.0-test"
description = "Manual acceptance helper for ItemGuard 1.0. Not a product plugin."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly(fileTree("../build/libs") {
        include("ItemGuard-*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveBaseName.set("ItemGuardTestPlugin")
    archiveVersion.set(project.version.toString())
}
