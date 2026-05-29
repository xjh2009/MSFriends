"""Build 1.15.2 forge fatJar (with bytecode downgrade)."""
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

log_path = os.path.join(proj, 'build-1152-forge-final.log')
env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Program Files\Zulu\zulu-21'
env['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'

cmd = [os.path.join(proj, 'gradlew.bat'), 
       ':versions:1.15.2:forge:assemble', 
       '--no-daemon', '--configure-on-demand', '--rerun-tasks']
print("Building fatJar with bytecode downgrade...")
r = subprocess.run(cmd, cwd=proj, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=600)
stdout = r.stdout or ''
stderr = r.stderr or ''
with open(log_path, 'w', encoding='utf-8') as f:
    f.write(stdout)
    f.write(stderr)
print(f"Exit code: {r.returncode}")
lines = stdout.split('\n')
for line in lines[-15:]:
    print(line[:200])
