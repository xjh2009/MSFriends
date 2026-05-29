#!/usr/bin/env python3
"""Update OVERLOADED_METHODS and safety check in patch_refmap_jar.py"""
import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'tools', 'patch_refmap_jar.py')
with open(path, encoding='utf-8') as f:
    content = f.read()

# Fix 1: Update OVERLOADED_METHODS
old_dict = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'addDrawableChild': 'm_142416_',\n"
    "    'addDrawable': 'm_169388_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
new_dict = (
    "OVERLOADED_METHODS = {\n"
    "    'translatable': 'm_237113_',\n"
    "    'getMessage': 'm_5646_',\n"
    "}"
)
if old_dict in content:
    content = content.replace(old_dict, new_dict)
    print('Fixed OVERLOADED_METHODS')
else:
    print('OVERLOADED_METHODS already fixed or not found')

# Fix 2: Change safety check to allow dev/msf/** classes (our own mod)
old_check = "if class_name.startswith('java/'):"
new_check = "if class_name.startswith('java/') or class_name.startswith('sun/') or class_name.startswith('jdk/'):"
if old_check in content:
    content = content.replace(old_check, new_check)
    print('Fixed safety check')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
