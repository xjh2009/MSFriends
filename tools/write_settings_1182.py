#!/usr/bin/env python3
"""Write settings.gradle.kts for Forge 1.18.2 build."""
import os
import shutil

project_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

settings_content = """pluginManagement {
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
include(":versions:1.18.2:common")
include(":versions:1.18.2:fabric")
include(":versions:1.18.2:forge")
"""

settings_path = os.path.join(project_dir, "settings.gradle.kts")
build_file = os.path.join(project_dir, "versions", "1.18.2", "forge", "build.gradle.kts")
build_bak = build_file + ".bak"

with open(settings_path, "w", encoding="utf-8") as f:
    f.write(settings_content)
print(f"Wrote {settings_path}")

if os.path.exists(build_bak):
    shutil.copy2(build_bak, build_file)
    print(f"Copied .bak -> build.gradle.kts")

print("Done")
