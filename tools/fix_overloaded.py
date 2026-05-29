#!/usr/bin/env python3
"""Update OVERLOADED_METHODS in patch_refmap_jar.py"""
import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'tools', 'patch_refmap_jar.py')
with open(path, encoding='utf-8') as f:
    content = f.read()

old = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'addDrawableChild': 'm_142416_',\n"
    "    'addDrawable': 'm_169388_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
new = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
if old in content:
    content = content.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Fixed OVERLOADED_METHODS')
else:
    print('Already fixed or not found')
