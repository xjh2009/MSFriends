import os
import subprocess

proj = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi'
settings = os.path.join(proj, 'settings.gradle.kts')
content = (
    'pluginManagement {\n'
    '    repositories {\n'
    '        gradlePluginPortal()\n'
    '        mavenCentral()\n'
    '        maven { url = uri("https://maven.fabricmc.net/") }\n'
    '        maven { url = uri("https://maven.neoforged.net/releases/") }\n'
    '        maven { url = uri("https://maven.minecraftforge.net/") }\n'
    '        maven { url = uri("https://repo.spongepowered.org/maven/") }\n'
    '    }\n'
    '}\n'
    '\n'
    'dependencyResolutionManagement {\n'
    '    repositories {\n'
    '        mavenCentral()\n'
    '        maven { url = uri("https://maven.fabricmc.net/") }\n'
    '        maven { url = uri("https://libraries.minecraft.net/") }\n'
    '        maven { url = uri("https://maven.minecraftforge.net/") }\n'
    '    }\n'
    '}\n'
    '\n'
    'rootProject.name = "MSF"\n'
    '\n'
    'include(":common")\n'
    'include(":versions:1.15.2:common")\n'
    'include(":versions:1.15.2:forge")\n'
)

with open(settings, 'w', encoding='utf-8') as f:
    f.write(content)

# Verify
with open(settings, 'r', encoding='utf-8') as f:
    check = f.read()
assert '1.15.2' in check, 'Failed to write 1.15.2 into settings!'
print('Settings written with 1.15.2 modules')
