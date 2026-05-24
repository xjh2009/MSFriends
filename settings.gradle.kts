pluginManagement {
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

// ---- shared pure logic (no MC dependency) ----
include(":common")

// ---- MC 1.18.2 modules ----
include(":versions:1.18.2:common")
include(":versions:1.18.2:fabric")

// ---- MC 1.16.5 modules ----
include(":versions:1.16.5:common")
include(":versions:1.16.5:fabric")

// ---- MC 1.20.1 modules ----
include(":versions:1.20.1:common")
include(":versions:1.20.1:fabric")

// ---- MC 1.21.1 modules ----
include(":versions:1.21.1:common")
include(":versions:1.21.1:fabric")

// ---- MC 1.21.11 modules ----
include(":versions:1.21.11:common")
include(":versions:1.21.11:fabric")
include(":versions:1.21.11:forge")
include(":versions:1.21.11:neoforge")

// ---- MC 26.1.2 modules ----
include(":versions:26.1.2:common")
include(":versions:26.1.2:fabric")
include(":versions:26.1.2:forge")
include(":versions:26.1.2:neoforge")

// ---- MC 1.19.2 modules ----
include(":versions:1.19.2:common")
include(":versions:1.19.2:fabric")
