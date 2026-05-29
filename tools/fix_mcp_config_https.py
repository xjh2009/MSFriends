#!/usr/bin/env python3
"""
Fix the mcp_config zip in the Mavenizer cache to use HTTPS URLs.
This prevents Mavenizer from downloading tools via HTTP (which returns 501).
"""
import os
import sys
import json
import zipfile
import hashlib
import io
import urllib.request

GRADLE_CACHE = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches'
MCP_CONFIG_VERSION = '1.14.4-20190829.143755'
FORGE_MAVEN_ZIP = f'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/{MCP_CONFIG_VERSION}/mcp_config-{MCP_CONFIG_VERSION}.zip'
FORGE_MAVEN_SHA1 = f'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/{MCP_CONFIG_VERSION}/mcp_config-{MCP_CONFIG_VERSION}.zip.sha1'

MCP_TOOLS_DIR = os.path.join(GRADLE_CACHE, 'maven', 'mcp-tools')
FORGE_DIR = os.path.join(GRADLE_CACHE, 'maven', 'forge')


def download(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    return urllib.request.urlopen(req, timeout=60).read()


def fix_mcp_config_zip():
    """Download mcp_config zip, patch HTTP URLs to HTTPS, re-save."""
    zip_dir = os.path.join(FORGE_DIR, 'de', 'oceanlabs', 'mcp', 'mcp_config', MCP_CONFIG_VERSION)
    zip_path = os.path.join(zip_dir, f'mcp_config-{MCP_CONFIG_VERSION}.zip')
    sha1_path = zip_path + '.sha1'
    
    os.makedirs(zip_dir, exist_ok=True)
    
    # Download original zip from Forge maven (HTTPS)
    print(f'Downloading mcp_config zip from Forge maven...')
    zip_data = download(FORGE_MAVEN_ZIP)
    print(f'  Downloaded {len(zip_data)} bytes')
    
    # Read and patch config.json
    zip_in = zipfile.ZipFile(io.BytesIO(zip_data))
    config = json.loads(zip_in.read('config.json'))
    
    patched = False
    for func_name, func in config.get('functions', {}).items():
        repo = func.get('repo', '')
        if repo.startswith('http://'):
            old = func['repo']
            func['repo'] = repo.replace('http://', 'https://')
            print(f'  Patched {func_name}: {old} -> {func["repo"]}')
            patched = True
    
    if not patched:
        print('  No HTTP URLs found to patch')
        return
    
    # Re-create zip with patched config.json
    print('  Re-creating zip with patched config.json...')
    buf = io.BytesIO()
    zip_out = zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED)
    for item in zip_in.namelist():
        if item == 'config.json':
            zip_out.writestr(item, json.dumps(config, indent=2))
        else:
            zip_out.writestr(item, zip_in.read(item))
    zip_out.close()
    patched_data = buf.getvalue()
    
    with open(zip_path, 'wb') as f:
        f.write(patched_data)
    
    patched_sha1 = hashlib.sha1(patched_data).hexdigest()
    with open(sha1_path, 'w') as f:
        f.write(patched_sha1)
    
    print(f'  Saved patched zip: {len(patched_data)} bytes, SHA1={patched_sha1}')


def fix_all_mcp_tools():
    """Download ALL 1.14.4 MCP tool jars via HTTPS and ensure they're valid."""
    # These are the tools used by 1.14.4 mcp_config
    tools = [
        {
            'path': 'net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar',
            'url': 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar',
            'sha1_url': 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar.sha1',
        },
        {
            'path': 'net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar',
            'url': 'https://maven.minecraftforge.net/net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar',
            'sha1_url': 'https://maven.minecraftforge.net/net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar.sha1',
        },
        {
            'path': 'de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar',
            'url': 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar',
            'sha1_url': 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar.sha1',
        },
        {
            'path': 'net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar',
            'url': 'https://maven.minecraftforge.net/net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar',
            'sha1_url': 'https://maven.minecraftforge.net/net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar.sha1',
        },
    ]
    
    for tool in tools:
        path = os.path.join(MCP_TOOLS_DIR, tool['path'])
        sha1_path = path + '.sha1'
        
        valid = False
        if os.path.exists(path) and os.path.getsize(path) > 1000:
            with open(path, 'rb') as f:
                if f.read(2) == b'PK':
                    valid = True
        
        if valid:
            print(f'  OK: {os.path.basename(path)}')
            continue
        
        print(f'  Downloading {os.path.basename(path)}...')
        data = download(tool['url'])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'wb') as f:
            f.write(data)
        
        # Get server SHA1
        try:
            server_sha1 = download(tool['sha1_url']).decode().strip().split()[0]
        except:
            server_sha1 = hashlib.sha1(data).hexdigest()
        
        with open(sha1_path, 'w') as f:
            f.write(server_sha1)
        
        print(f'    OK: {len(data)} bytes')


if __name__ == '__main__':
    print('=== Fix mcp_config zip URLs ===')
    fix_mcp_config_zip()
    print()
    print('=== Fix MCP tool jars ===')
    fix_all_mcp_tools()
    print()
    print('Done! Run the build now.')
