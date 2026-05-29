import os, zipfile

# Search ALL gradle caches for mcp_config 1.14.4
caches = [
    r'C:\Users\xjh37\.gradle\caches\modules-2',
    r'C:\Users\xjh37\.gradle\caches\minecraftforge',
]

for cache in caches:
    if not os.path.exists(cache):
        continue
    for root, dirs, files in os.walk(cache):
        for f in files:
            if 'mcp_config' in root.lower() and '1.14' in root:
                path = os.path.join(root, f)
                size = os.path.getsize(path)
                print(f'{size:>10} - {f} - {os.path.relpath(path, cache)[:120]}')
