import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'settings_f1192.gradle.kts')
lines = [
    'pluginManagement {',
    '    repositories {',
    '        gradlePluginPortal()',
    '        mavenCentral()',
    '        maven { url = uri("https://maven.fabricmc.net/") }',
    '        maven { url = uri("https://maven.neoforged.net/releases/") }',
    '        maven { url = uri("https://maven.minecraftforge.net/") }',
    '        maven { url = uri("https://repo.spongepowered.org/maven/") }',
    '    }',
    '}',
    '',
    'dependencyResolutionManagement {',
    '    repositories {',
    '        mavenCentral()',
    '        maven { url = uri("https://maven.fabricmc.net/") }',
    '        maven { url = uri("https://libraries.minecraft.net/") }',
    '        maven { url = uri("https://maven.minecraftforge.net/") }',
    '    }',
    '}',
    '',
    'rootProject.name = "MSF"',
    '',
    '// ---- shared pure logic (no MC dependency) ----',
    'include(":common")',
    '',
    '// ---- MC 1.19.2 modules ----',
    'include(":versions:1.19.2:common")',
    'include(":versions:1.19.2:forge")',
    '',
]
content = chr(10).join(lines)
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Written', len(content), 'chars to', path)
