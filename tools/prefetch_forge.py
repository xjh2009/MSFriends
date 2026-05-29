#!/usr/bin/env python3
"""Download critical ForgeGradle Mavenizer artifacts for 1.14.4."""
import urllib.request, os, hashlib, zipfile, time

CACHE = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge'

ARTIFACTS = [
    {
        'group': 'net.minecraftforge',
        'artifact': 'forge',
        'version': '1.14.4-28.2.30',
        'classifier': 'userdev',
        'ext': 'jar',
        'repos': ['https://maven.minecraftforge.net'],
    },
    {
        'group': 'de.oceanlabs.mcp',
        'artifact': 'mcp_config',
        'version': '1.14.4-20190829.143755',
        'classifier': '',
        'ext': 'zip',
        'repos': ['https://maven.minecraftforge.net'],
    },
]

for art in ARTIFACTS:
    group_path = art['group'].replace('.', os.sep)
    if art['classifier']:
        filename = f'{art["artifact"]}-{art["version"]}-{art["classifier"]}.{art["ext"]}'
    else:
        filename = f'{art["artifact"]}-{art["version"]}.{art["ext"]}'
    
    local_path = os.path.join(CACHE, group_path, art['artifact'], art['version'], filename)
    sha_path = local_path + '.sha1'
    
    if os.path.exists(local_path):
        try:
            with zipfile.ZipFile(local_path) as z:
                print(f'OK (cached): {filename} ({os.path.getsize(local_path)} bytes, {len(z.namelist())} entries)')
                continue
        except:
            os.remove(local_path)
    
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    
    group_url = art['group'].replace('.', '/')
    for repo in art['repos']:
        url = f'{repo}/{group_url}/{art["artifact"]}/{art["version"]}/{filename}'
        print(f'Downloading {filename} from {repo}...')
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
            
            with open(local_path, 'wb') as f:
                f.write(data)
            
            sha1 = hashlib.sha1(data).hexdigest()
            with open(sha_path, 'w') as f:
                f.write(sha1)
            
            with zipfile.ZipFile(local_path) as z:
                entries = len(z.namelist())
            
            print(f'  OK: {len(data)} bytes, {entries} entries, SHA1: {sha1}')
            break
        except Exception as e:
            print(f'  FAILED: {e}')

# Create .cache files
now = str(int(time.time()))
for art in ARTIFACTS:
    group_path = art['group'].replace('.', os.sep)
    if art['classifier']:
        filename = f'{art["artifact"]}-{art["version"]}-{art["classifier"]}.{art["ext"]}'
    else:
        filename = f'{art["artifact"]}-{art["version"]}.{art["ext"]}'
    
    local_path = os.path.join(CACHE, group_path, art['artifact'], art['version'], filename)
    cache_path = local_path + '.cache'
    if not os.path.exists(cache_path):
        with open(cache_path, 'w') as f:
            f.write(now)

print('\nDone.')
