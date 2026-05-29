const fs = require("fs");
const path = require("path");

const settingsPath = path.join("c:\\Users\\xjh37\\Desktop\\MSF\\msf-friends-multi", "settings.gradle.kts");

const content = `pluginManagement {
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

fs.writeFileSync(settingsPath, content, "utf-8");
console.log("Written", fs.statSync(settingsPath).size, "bytes to settings.gradle.kts");
