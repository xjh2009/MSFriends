#!/usr/bin/env python3
"""Write minimal settings.gradle.kts with only 1.14.4:forge."""
import os

path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'settings.gradle.kts')

content = """pluginManagement {
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

// ---- MC 1.14.4 ----
include(":versions:1.14.4:forge")
"""

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print(f'Written {len(content)} bytes to {path}')

# Verify
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
print(f'Total lines: {len(lines)}')
for line in lines:
    stripped = line.rstrip()
    if 'include' in stripped:
        print(f'  {"ACTIVE" if not stripped.startswith("//") else "disabled"}: {stripped}')
