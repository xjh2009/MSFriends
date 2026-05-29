"""Write settings.gradle.kts for 1.15.2 and run Forge compileJava."""
import os, subprocess, sys

proj = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi'
settings = os.path.join(proj, 'settings.gradle.kts')

# Read existing to see what's there
with open(settings, 'r', encoding='utf-8') as f:
    existing = f.read()
print(f"Current settings includes: {[l.strip() for l in existing.splitlines() if 'include' in l]}")

# Write new settings
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

# Immediately run gradle
env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Program Files\Zulu\zulu-21'
env['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'
cmd = [os.path.join(proj, 'gradlew.bat'), 
       ':versions:1.15.2:forge:compileJava', 
       '--no-daemon', '--configure-on-demand']

print(f"Running: {' '.join(cmd)}")
result = subprocess.run(cmd, cwd=proj, env=env, capture_output=True, text=True, timeout=900)
print("STDOUT:")
print(result.stdout[-3000:] if len(result.stdout) > 3000 else result.stdout)
print("STDERR:")
print(result.stderr[-3000:] if len(result.stderr) > 3000 else result.stderr)
print(f"Exit code: {result.returncode}")
