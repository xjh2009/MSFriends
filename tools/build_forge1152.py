#!/usr/bin/env python3
"""Build Forge 1.15.2 by writing settings.gradle.kts and immediately running gradle."""
import subprocess, sys, os, base64, time

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

SETTINGS_CONTENT = """pluginManagement {
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

// ---- MC 1.15.2 modules ----
include(":versions:1.15.2:common")
include(":versions:1.15.2:fabric")
include(":versions:1.15.2:forge")
"""

# Write the settings file
with open("settings.gradle.kts", "w", encoding="utf-8") as f:
    f.write(SETTINGS_CONTENT)
print(f"Wrote settings.gradle.kts ({len(SETTINGS_CONTENT)} chars)")

# Verify
with open("settings.gradle.kts", "r", encoding="utf-8") as f:
    content = f.read()
    has_forge = ":versions:1.15.2:forge" in content
    print(f"Verified: has forge = {has_forge}")

# Immediately run gradle (settings is read during configure, which happens after process starts)
gradle_args = sys.argv[1:] if len(sys.argv) > 1 else [":versions:1.15.2:forge:assemble"]
cmd = ["cmd", "/c", "gradlew.bat"] + gradle_args
print(f"Running: {' '.join(cmd)}")
sys.stdout.flush()

result = subprocess.run(cmd)
sys.exit(result.returncode)
