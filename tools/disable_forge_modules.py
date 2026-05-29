#!/usr/bin/env python3
"""Disable all forge/neoforge build.gradle.kts except 1.14.4."""
import os, shutil

base = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions'

disabled = [
    '1.13.2/forge',
    '1.15.2/forge',
    '1.16.5/forge',
    '1.17.1/forge',
    '1.18.2/forge',
    '1.19.2/forge',
    '1.20.1/forge',
    '1.21.1/neoforge',
    '1.21.11/neoforge',
    '26.1.2/neoforge',
]

for entry in disabled:
    gradle = os.path.join(base, entry, 'build.gradle.kts')
    if not os.path.exists(gradle):
        print(f'SKIP (not found): {entry}')
        continue
    
    with open(gradle, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if 'disabled' in content.split('\n')[0].lower():
        print(f'ALREADY DISABLED: {entry}')
        continue
    
    # Backup
    bak = gradle + '.bak2'
    if not os.path.exists(bak):
        shutil.copy2(gradle, bak)
    
    with open(gradle, 'w', encoding='utf-8', newline='\n') as f:
        f.write(f'// {entry} disabled for 1.14.4 build test\n')
    
    print(f'DISABLED: {entry}')

print('\nDone.')
