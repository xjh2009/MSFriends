#!/usr/bin/env python3
"""Rebuild settings.gradle.kts with all modules and 1.14.4:forge enabled."""
import os

path = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\settings.gradle.kts'

content = '''pluginManagement {
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

// ---- MC 1.10 ----
// include(":versions:1.10:common")
// include(":versions:1.10:fabric")

// ---- MC 1.10.2 ----
// include(":versions:1.10.2:common")
// include(":versions:1.10.2:fabric")

// ---- MC 1.11.2 ----
// include(":versions:1.11.2:common")
// include(":versions:1.11.2:fabric")

// ---- MC 1.12 ----
// include(":versions:1.12:common")
// include(":versions:1.12:fabric")

// ---- MC 1.12.2 ----
// include(":versions:1.12.2:common")
// include(":versions:1.12.2:fabric")

// ---- MC 1.13.2 ----
// include(":versions:1.13.2:common")
// include(":versions:1.13.2:fabric")
// include(":versions:1.13.2:forge")

// ---- MC 1.14.4 ----
include(":versions:1.14.4:common")
include(":versions:1.14.4:fabric")
include(":versions:1.14.4:forge")

// ---- MC 1.15.2 ----
include(":versions:1.15.2:common")
include(":versions:1.15.2:fabric")
// include(":versions:1.15.2:forge")

// ---- MC 1.16.5 ----
include(":versions:1.16.5:common")
include(":versions:1.16.5:fabric")
// include(":versions:1.16.5:forge")

// ---- MC 1.17.1 ----
include(":versions:1.17.1:common")
include(":versions:1.17.1:fabric")
// include(":versions:1.17.1:forge")

// ---- MC 1.18.2 ----
include(":versions:1.18.2:common")
include(":versions:1.18.2:fabric")
// include(":versions:1.18.2:forge")

// ---- MC 1.19.2 ----
include(":versions:1.19.2:common")
include(":versions:1.19.2:fabric")
// include(":versions:1.19.2:forge")

// ---- MC 1.20.1 ----
include(":versions:1.20.1:common")
include(":versions:1.20.1:fabric")
// include(":versions:1.20.1:forge")

// ---- MC 1.21.1 ----
include(":versions:1.21.1:common")
include(":versions:1.21.1:fabric")
// include(":versions:1.21.1:neoforge")

// ---- MC 1.21.11 ----
include(":versions:1.21.11:common")
include(":versions:1.21.11:fabric")
// include(":versions:1.21.11:neoforge")

// ---- MC 26.1.2 ----
include(":versions:26.1.2:common")
include(":versions:26.1.2:fabric")
// include(":versions:26.1.2:neoforge")
'''

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print(f'Written {len(content)} bytes to settings.gradle.kts')

# Verify
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
print(f'Total lines: {len(lines)}')
for line in lines:
    stripped = line.rstrip()
    if '1.14' in stripped or ('include' in stripped and 'forge' in stripped.lower() and not stripped.startswith('//')):
        print(f'  ACTIVE: {stripped}')
    elif 'include' in stripped and stripped.startswith('//'):
        pass  # Skip commented includes
