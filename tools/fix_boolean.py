#!/usr/bin/env python3
"""Add BOOLEAN field mapping to patch_refmap_jar.py"""
import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'tools', 'patch_refmap_jar.py')
with open(path, encoding='utf-8') as f:
    content = f.read()

old = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
new = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "    'BOOLEAN': 'f_231471_',\n"
    "    'toButton': 'm_231549_',\n"
    "}"
)
if old in content:
    content = content.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Added BOOLEAN mapping')
else:
    print('Already updated or not found')
