#!/usr/bin/env node
/**
 * Atomic build script for Forge 1.17.1
 * Writes settings.gradle.kts and build.gradle.kts, then runs the Gradle build.
 */
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const ROOT = "c:\\Users\\xjh37\\Desktop\\MSF\\msf-friends-multi";

// 1. Write settings.gradle.kts
const settings = `pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://repo.spongepowered.org/maven/") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
    }
}

rootProject.name = "MSF"

include(":common")

include(":versions:1.17.1:common")
include(":versions:1.17.1:forge")
`;

// 2. Write build.gradle.kts for forge 1.17.1
const buildGradle = `import java.net.URI

plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.17.1 entry point"

minecraft {
    useDefaultAccessTransformer()
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        when {
            requested.group == "cpw.mods" && requested.name == "modlauncher" -> useVersion("9.0.7")
            requested.group == "cpw.mods" && requested.name == "securejarhandler" -> useVersion("0.9.29")
            requested.group == "net.minecraftforge" && requested.name == "forgespi" -> useVersion("4.0.10")
            requested.group == "net.minecraftforge" && requested.name == "coremods" -> useVersion("5.0.1")
            requested.group == "net.minecraftforge" && requested.name == "eventbus" -> useVersion("5.0.7")
            requested.group == "org.apache.logging.log4j" && requested.name == "log4j-api" -> useVersion("2.17.0")
            requested.group == "org.apache.logging.log4j" && requested.name == "log4j-core" -> useVersion("2.17.0")
        }
    }
}

dependencies {
    implementation(project(":versions:1.17.1:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.17.1-37.1.1"))

    implementation("net.minecraftforge:javafmllanguage:1.17.1-37.1.1")
    implementation("net.minecraftforge:fmlloader:1.17.1-37.1.1")
    implementation("net.minecraftforge:fmlcore:1.17.1-37.1.1")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.17.1",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.17.1",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "MOD"
)

tasks.named<Jar>("jar") {
    manifest { attributes(forgeManifestAttrs) }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a deployable fat jar containing all module classes"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes(forgeManifestAttrs) }
    from(sourceSets.main.get().output)
    val commonJar = project(":common").tasks.named<Jar>("jar")
    val verCommonJar = project(":versions:1.17.1:common").tasks.named<Jar>("jar")
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }

    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }

    exclude("com/mojang/**")
}
`;

console.log("Writing settings.gradle.kts...");
fs.writeFileSync(path.join(ROOT, "settings.gradle.kts"), settings, "utf-8");
console.log("Writing versions/1.17.1/forge/build.gradle.kts...");
fs.writeFileSync(path.join(ROOT, "versions", "1.17.1", "forge", "build.gradle.kts"), buildGradle, "utf-8");
console.log("Files written successfully.");
