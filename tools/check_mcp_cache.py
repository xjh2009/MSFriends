import os, json

cache = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches'
# Check the maven/mcp-tools directory structure
mcp_tools = os.path.join(cache, 'maven', 'mcp-tools')
if os.path.exists(mcp_tools):
    for root, dirs, files in os.walk(mcp_tools):
        for f in files:
            path = os.path.join(root, f)
            rel = os.path.relpath(path, mcp_tools)
            size = os.path.getsize(path)
            print(f'{size:>10} - {rel}')
else:
    print('mcp-tools dir not found')

# Also check for the specific rename-related artifacts
rename_dir = os.path.join(cache, 'minecraft_tasks', '1.14.4')
if os.path.exists(rename_dir):
    print('\n1.14.4 tasks:')
    for f in os.listdir(rename_dir):
        print(f'  {f}')
else:
    print('No 1.14.4 task cache')

# Check if there's a mcp_config entry for 1.14.4
mcp_dir = os.path.join(cache, 'mcp')
for root, dirs, files in os.walk(mcp_dir):
    if '1.14' in root:
        for f in files:
            path = os.path.join(root, f)
            size = os.path.getsize(path)
            print(f'MCP: {size:>10} - {path}')
