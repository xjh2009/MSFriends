"""Build 1.15.2 and save output to file."""
import os, subprocess

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

log_path = os.path.join(proj, 'build-1152-common-compile.log')
env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Program Files\Zulu\zulu-21'
env['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'

cmd = f'cmd /c "set JAVA_HOME=C:\\Program Files\\Zulu\\zulu-21 && set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 && .\\gradlew.bat :versions:1.15.2:common:compileJava --no-daemon --configure-on-demand --stacktrace 2>&1 > {log_path}"'
print(f"Running build, log to: {log_path}")
os.system(f'cd /d {proj} && {cmd}')
print("Done")
