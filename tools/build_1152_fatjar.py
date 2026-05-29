"""Build 1.15.2 forge fatJar and save output to file."""
import os

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
print("Settings written")

log_path = os.path.join(proj, 'build-1152-forge-fatjar.log')
cmd = (
    f'cd /d "{proj}" && '
    f'set "JAVA_HOME=C:\\Program Files\\Zulu\\zulu-21" && '
    f'set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8" && '
    f'gradlew.bat :versions:1.15.2:forge:assemble --no-daemon --configure-on-demand '
    f'> "{log_path}" 2>&1'
)
print(f"Building fatJar, log: {log_path}")
exit_code = os.system(cmd)
print(f"Exit code: {exit_code}")
