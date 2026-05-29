"""Build 1.15.2 and capture detailed errors."""
import os, subprocess, sys

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

env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Program Files\Zulu\zulu-21'
env['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'

# Build common first
cmd = [os.path.join(proj, 'gradlew.bat'), 
       ':versions:1.15.2:common:compileJava', 
       '--no-daemon', '--configure-on-demand', '--stacktrace']
print(f"Running: {' '.join(cmd)}")
r = subprocess.run(cmd, cwd=proj, env=env, capture_output=True, text=True, timeout=600)
stdout = r.stdout or ''
stderr = r.stderr or ''
print("=== STDOUT (last 3000 chars) ===")
print(stdout[-3000:])
print("=== STDERR (last 3000 chars) ===")
print(stderr[-3000:])
print(f"Exit code: {r.returncode}")
