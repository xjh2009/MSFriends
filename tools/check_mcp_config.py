#!/usr/bin/env python3
"""Analyze mcp_config to find tool definitions for rename step."""
import urllib.request, zipfile, json, io, sys

url = 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.14.4-20190829.143755/mcp_config-1.14.4-20190829.143755.zip'
print("Downloading mcp_config zip...")
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
data = urllib.request.urlopen(req).read()
print(f"Downloaded {len(data)} bytes")

z = zipfile.ZipFile(io.BytesIO(data))
with z.open('config.json') as f:
    config = json.load(f)

print(f"\nSpec: {config.get('spec')}")
print(f"Version: {config.get('version')}")
print(f"Type: {config.get('type')}")

# Print all top-level keys
print(f"\nTop-level keys: {list(config.keys())}")

# Print data keys
if 'data' in config:
    print("\nData entries:")
    for k, v in config['data'].items():
        print(f"  {k}: {v}")

# Check for functions (spec >= 2)
if 'functions' in config:
    print("\nFunctions:")
    for name, func in config['functions'].items():
        print(f"  {name}:")
        for k, v in func.items():
            if k != 'args':
                print(f"    {k}: {v}")
            else:
                print(f"    args: {v}")
else:
    print("\nNo 'functions' key found - this is spec 1 MCP config")
    # In spec 1, tools are embedded in steps
    # Look at the step types and their associated tool info

# Print steps
print("\nSteps:")
for side_name, steps in config.get('steps', {}).items():
    print(f"\n  Side: {side_name}")
    for i, step in enumerate(steps):
        typ = step.get('type', '?')
        name = step.get('name', typ)
        tool = step.get('tool', None)
        version = step.get('version', None)
        repo = step.get('repo', None)
        func = step.get('function', None)
        
        info = f"    [{i}] {name} (type={typ})"
        if tool:
            info += f" tool={tool}"
        if version:
            info += f" version={version}"
        if repo:
            info += f" repo={repo}"
        if func:
            info += f" function={json.dumps(func)}"
        # Also check for 'libraries' in step
        libs = step.get('libraries', None)
        if libs:
            info += f" libraries={libs}"
        print(info)
        
        # For the rename step, print ALL keys
        if typ == 'rename' or name == 'rename':
            print(f"         ALL KEYS: {json.dumps(step)}")
