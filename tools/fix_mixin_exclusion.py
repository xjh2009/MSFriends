#!/usr/bin/env python3
"""Fix safety check to exclude mixin classes from bare-name patching"""
import os

bak = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts.bak'
with open(bak, encoding='utf-8') as f:
    content = f.read()

# Revert the replacements.containsKey bypass - it was wrong
old1 = 'replacements.containsKey(entry.value) && !classFile.absolutePath.contains("mixin")'
new1 = 'replacements.containsKey(entry.value)'
count = content.count(old1)
if count > 0:
    content = content.replace(old1, new1)
    print(f'Reverted containsKey bypass ({count} occurrences)')

# Now add proper mixin exclusion in the walkTopDown loop
old2 = '''        var patchedCount = 0
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            val patched = patchClassConstantPool(original, pathQualified, bareNames, mcClassInternalNames)'''

new2 = '''        var patchedCount = 0
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            // Mixin classes: skip bare-name replacement to let mixin transformer
            // handle method/field remapping via refmap at runtime.
            val isMixin = classFile.absolutePath.contains("mixin")
            val effectiveBare = if (isMixin) emptyList() else bareNames
            val patched = patchClassConstantPool(original, pathQualified, effectiveBare, mcClassInternalNames)'''

if old2 in content:
    content = content.replace(old2, new2)
    print('Added mixin class exclusion')
else:
    print('Already applied or not found')

with open(bak, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
