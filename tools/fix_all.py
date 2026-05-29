#!/usr/bin/env python3
"""Revert safety check and fix alpha/width/height/x/y in IconButtonWidget"""
import os

# Fix 1: Revert safety check in build.gradle.kts.bak
bak = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'versions', '1.19.2', 'forge', 'build.gradle.kts.bak')
with open(bak, encoding='utf-8') as f:
    content = f.read()

old = 'if (!className.startsWith("dev/msf/") && className !in mcClassNames) {'
new = 'if (className !in mcClassNames) {'
if old in content:
    content = content.replace(old, new)
    with open(bak, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Reverted safety check (removed dev/msf/ exception)')
else:
    print('Safety check already reverted or not found')

# Fix 2: Update patch_refmap_jar.py to add alpha/width/height/x/y fields
patch = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'tools', 'patch_refmap_jar.py')
with open(patch, encoding='utf-8') as f:
    content = f.read()

old2 = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
new2 = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}\n"
    "\n"
    "# Fields inherited from MC classes that our code references via dev/msf/ class owners.\n"
    "# These can't be handled by patchClassConstantPool safety check (which only allows MC class owners).\n"
    "OVERLOADED_FIELDS = {\n"
    "    'alpha': 'f_93625_',\n"
    "    'x': 'f_96545_',\n"
    "    'y': 'f_96546_',\n"
    "    'width': 'f_96543_',\n"
    "    'height': 'f_96544_',\n"
    "}"
)
if old2 in content:
    content = content.replace(old2, new2)
    with open(patch, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Added OVERLOADED_FIELDS dict')
else:
    print('OVERLOADED_METHODS already updated or not found')

print('Done')
