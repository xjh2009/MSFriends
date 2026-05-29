#!/usr/bin/env python3
"""Pre-download MCP tool jars that Mavenizer needs for 1.14.4 processing."""
import os, hashlib, urllib.request, zipfile

CACHE_BASE = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mcp-tools'

# Tools needed for 1.14.4 MCP pipeline
TOOLS = [
    # SpecialSource for rename (obf -> SRG)
    {
        'path': r'net\md-5\SpecialSource\1.8.3\SpecialSource-1.8.3-shaded.jar',
        'url': 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar',
    },
    # ForgeAutoRenamingTool for mapped rename  
    {
        'path': r'net\minecraftforge\ForgeAutoRenamingTool\0.1.17\ForgeAutoRenamingTool-0.1.17-all.jar',
        'url': 'https://maven.minecraftforge.net/net/minecraftforge/ForgeAutoRenamingTool/0.1.17/ForgeAutoRenamingTool-0.1.17-all.jar',
    },
    # mcinjector for parameter injection
    {
        'path': r'de\oceanlabs\mcp\mcinjector\3.8.0\mcinjector-3.8.0-fatjar.jar',
        'url': 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcinjector/3.8.0/mcinjector-3.8.0-fatjar.jar',
    },
    # installertools for access/stripSides
    {
        'path': r'net\minecraftforge\installertools\1.2.0\installertools-1.2.0-fatjar.jar',
        'url': 'https://maven.minecraftforge.net/net/minecraftforge/installertools/1.2.0/installertools-1.2.0-fatjar.jar',
    },
    # ForgeFlower decompiler
    {
        'path': r'net\minecraftforge\forgeflower\1.5.478.16\forgeflower-1.5.478.16.jar',
        'url': 'https://maven.minecraftforge.net/net/minecraftforge/forgeflower/1.5.478.16/forgeflower-1.5.478.16.jar',
    },
    # MergeTool
    {
        'path': r'net\minecraftforge\mergetool\1.0.1\mergetool-1.0.1-fatjar.jar',
        'url': 'https://maven.minecraftforge.net/net/minecraftforge/mergetool/1.0.1/mergetool-1.0.1-fatjar.jar',
    },
]

for tool in TOOLS:
    jar_path = os.path.join(CACHE_BASE, tool['path'])
    sha1_path = jar_path + '.sha1'
    
    # Check if already valid
    if os.path.exists(jar_path):
        try:
            with zipfile.ZipFile(jar_path, 'r') as z:
                z.testzip()
            print(f'OK (cached): {os.path.basename(tool["path"])}')
            continue
        except:
            os.remove(jar_path)
            if os.path.exists(sha1_path):
                os.remove(sha1_path)
    
    # Create directory
    os.makedirs(os.path.dirname(jar_path), exist_ok=True)
    
    # Download
    print(f'Downloading {os.path.basename(tool["path"])}...')
    try:
        req = urllib.request.Request(tool['url'], headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
        with open(jar_path, 'wb') as f:
            f.write(data)
        size = len(data)
        
        # Verify it's a valid jar
        with zipfile.ZipFile(jar_path, 'r') as z:
            entries = len(z.namelist())
        
        # Calculate and write SHA1
        with open(jar_path, 'rb') as f:
            sha1 = hashlib.sha1(f.read()).hexdigest()
        with open(sha1_path, 'w') as f:
            f.write(sha1)
        
        print(f'  OK: {size} bytes, {entries} entries, SHA1: {sha1}')
    except Exception as e:
        print(f'  FAILED: {e}')
        if os.path.exists(jar_path):
            os.remove(jar_path)

print('\nDone pre-downloading tools.')
