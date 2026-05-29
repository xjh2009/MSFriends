"""Build 1.15.2 forge fatJar."""
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

log_path = os.path.join(proj, 'build-1152-forge-fatjar.log')
env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Program Files\Zulu\zulu-21'
env['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'

cmd = [os.path.join(proj, 'gradlew.bat'), 
       ':versions:1.15.2:forge:assemble', 
       '--no-daemon', '--configure-on-demand']
print(f"Building fatJar...")
r = subprocess.run(cmd, cwd=proj, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=600)
stdout = r.stdout or ''
stderr = r.stderr or ''
# Save to file
with open(log_path, 'w', encoding='utf-8') as f:
    f.write("=== STDOUT ===\n")
    f.write(stdout)
    f.write("\n=== STDERR ===\n")
    f.write(stderr)
    f.write(f"\nExit code: {r.returncode}\n")
print(f"Exit code: {r.returncode}")
print(f"Log saved to: {log_path}")
# Print last lines
lines = stdout.split('\n')
print("=== Last 20 lines of stdout ===")
for line in lines[-20:]:
    print(line[:200])
