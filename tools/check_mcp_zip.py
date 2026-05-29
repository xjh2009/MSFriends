import os, time

# Monitor the mcp_config zip during Mavenizer execution
path = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_config\1.14.4-20190829.143755\mcp_config-1.14.4-20190829.143755.zip'

if os.path.exists(path):
    stat = os.stat(path)
    print(f'Before build: {stat.st_size} bytes, mtime={stat.st_mtime}')
else:
    print('File does not exist before build')

# Also check if there's a .cache file that triggers re-download
cache_file = path + '.cache'
if os.path.exists(cache_file):
    with open(cache_file, 'r') as f:
        print(f'.cache file: {repr(f.read())}')
else:
    print('No .cache file')

# Check the forge userdev zip
forge_dir = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.14.4-28.2.30'
if os.path.exists(forge_dir):
    for f in os.listdir(forge_dir):
        fp = os.path.join(forge_dir, f)
        print(f'Forge cache: {os.path.getsize(fp)} bytes - {f}')
else:
    print('Forge 1.14.4 not in cache')
