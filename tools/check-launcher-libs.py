import json, sys
sys.stdout.reconfigure(encoding='utf-8')
manifest = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\launcher_manifest.json'
with open(manifest) as f:
    data = json.load(f)
for lib in data.get('libraries', []):
    name = lib.get('name', '')
    if 'authlib' in name or 'fastutil' in name or 'jsr305' in name or 'javax.annotation' in name:
        print(name)
