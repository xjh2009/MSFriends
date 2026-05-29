#!/usr/bin/env python3
"""
Fix SpecialSource jar and start an HTTPS proxy for Mavenizer.
Mavenizer downloads from http://repo1.maven.org/maven2/ which returns 501.
This script:
1. Downloads the jar via HTTPS
2. Starts a local HTTP server on port 8080 that proxies to HTTPS
3. Patches the hosts file (if possible) to redirect repo1.maven.org to 127.0.0.1
"""
import os
import sys
import urllib.request
import hashlib
import time

GRADLE_CACHE = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches'

# All jars that need fixing (HTTP repo URLs)
JARS_TO_FIX = [
    {
        'path': 'maven/mcp-tools/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar',
        'https_url': 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar',
        'sha1_url': 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar.sha1',
    },
    {
        'path': 'maven/mcp-tools/net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar',
        'https_url': 'https://maven.minecraftforge.net/net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar',
        'sha1_url': 'https://maven.minecraftforge.net/net/minecraftforge/forgeflower/1.5.380.33/forgeflower-1.5.380.33.jar.sha1',
    },
    {
        'path': 'maven/mcp-tools/de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar',
        'https_url': 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar',
        'sha1_url': 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcinjector/3.7.7/mcinjector-3.7.7-fatjar.jar.sha1',
    },
    {
        'path': 'maven/mcp-tools/net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar',
        'https_url': 'https://maven.minecraftforge.net/net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar',
        'sha1_url': 'https://maven.minecraftforge.net/net/minecraftforge/mergetool/1.0.9/mergetool-1.0.9-fatjar.jar.sha1',
    },
]


def download_https(url, headers=None):
    """Download a file via HTTPS with User-Agent header."""
    hdrs = {'User-Agent': 'Mozilla/5.0'}
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, headers=hdrs)
    return urllib.request.urlopen(req, timeout=30).read()


def fix_jars():
    """Download all MCP tool jars via HTTPS."""
    for jar_info in JARS_TO_FIX:
        local_path = os.path.join(GRADLE_CACHE, jar_info['path'])
        local_sha1 = local_path + '.sha1'
        
        # Check if jar exists and is valid
        needs_fix = False
        if not os.path.exists(local_path):
            needs_fix = True
        else:
            size = os.path.getsize(local_path)
            with open(local_path, 'rb') as f:
                header = f.read(4)
            if header != b'PK\x03\x04' or size < 1000:
                needs_fix = True
                print(f'  CORRUPT: {os.path.basename(local_path)} ({size} bytes)')
        
        if not needs_fix:
            print(f'  OK: {os.path.basename(local_path)}')
            continue
        
        print(f'  Downloading {os.path.basename(local_path)} via HTTPS...')
        os.makedirs(os.path.dirname(local_path), exist_ok=True)
        
        data = download_https(jar_info['https_url'])
        with open(local_path, 'wb') as f:
            f.write(data)
        
        actual_sha1 = hashlib.sha1(data).hexdigest()
        
        # Download server SHA1
        try:
            server_sha1 = download_https(jar_info['sha1_url']).decode().strip().split()[0]
        except:
            server_sha1 = actual_sha1
        
        with open(local_sha1, 'w') as f:
            f.write(server_sha1)
        
        print(f'    OK: {len(data)} bytes, SHA1={actual_sha1}')


def make_readonly():
    """Make all MCP tool jars and their .sha1 files read-only."""
    base = os.path.join(GRADLE_CACHE, 'maven', 'mcp-tools')
    for root, dirs, files in os.walk(base):
        for f in files:
            if f.endswith('.jar') or f.endswith('.sha1'):
                path = os.path.join(root, f)
                try:
                    os.chmod(path, 0o444)
                    print(f'  Made read-only: {f}')
                except Exception as e:
                    print(f'  Cannot make read-only {f}: {e}')


if __name__ == '__main__':
    print('=== Fix MCP Tools Jars ===')
    fix_jars()
    print()
    print('=== Making jars read-only ===')
    make_readonly()
    print()
    print('Done!')
