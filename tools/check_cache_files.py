import os

# Before build: snapshot the mcp_config cache
mcp_dir = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_config\1.14.4-20190829.143755'
if os.path.exists(mcp_dir):
    for root, dirs, files in os.walk(mcp_dir):
        for f in files:
            path = os.path.join(root, f)
            print(f'{os.path.getsize(path):>10} {f}')

# Also check the .cache file that tells Mavenizer if it needs to re-download
cache_file = mcp_dir + '.cache'
if os.path.exists(cache_file):
    with open(cache_file, 'r') as f:
        print(f'.cache file: {f.read().strip()}')
else:
    print('No .cache file found')

# Check if there's a .cache file one level up
parent_cache = os.path.dirname(mcp_dir) + '.cache'
if os.path.exists(parent_cache):
    with open(parent_cache, 'r') as f:
        print(f'Parent .cache: {f.read().strip()}')

# Check all .cache files in the forge maven dir
forge_maven = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge'
for root, dirs, files in os.walk(forge_maven):
    for f in files:
        if f.endswith('.cache'):
            path = os.path.join(root, f)
            with open(path, 'r') as fh:
                content = fh.read().strip()
            print(f'CACHE: {os.path.relpath(path, forge_maven)} = {content}')
