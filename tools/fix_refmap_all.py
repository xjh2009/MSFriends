#!/usr/bin/env python3
"""
Apply all Forge 1.19.2 refmap fixes to build.gradle.kts from backup.
Fixes:
1. Convert refmap KEY class names (Yarn -> Mojang)
2. Add missing OptionsMixin refmap entry for @Shadow allKeys field
"""
import re

bak_path = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts.bak'
target = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts'

with open(bak_path, encoding='utf-8') as f:
    lines = f.readlines()

# Find the line: "val convertedText = ..."
convert_line_idx = None
for i, line in enumerate(lines):
    if 'val convertedText = refmapText.replace' in line:
        convert_line_idx = i
        break

if convert_line_idx is None:
    print("ERROR: Could not find 'val convertedText' line")
    exit(1)

# Change "val convertedText" to "var convertedText"
lines[convert_line_idx] = lines[convert_line_idx].replace('val convertedText', 'var convertedText')
print(f"Line {convert_line_idx+1}: Changed 'val' to 'var'")

# Find the closing of the replace block: "refmapFile.writeText(convertedText)"
write_idx = None
for i in range(convert_line_idx, min(convert_line_idx + 20, len(lines))):
    if 'refmapFile.writeText(convertedText)' in lines[i]:
        write_idx = i
        break

if write_idx is None:
    print("ERROR: Could not find 'refmapFile.writeText(convertedText)' line")
    exit(1)

# Find the lifecycle line after it
lifecycle_idx = None
for i in range(write_idx, min(write_idx + 3, len(lines))):
    if 'logger.lifecycle("Converted Fabric refmap' in lines[i]:
        lifecycle_idx = i
        break

if lifecycle_idx is None:
    print("ERROR: Could not find lifecycle line after writeText")
    exit(1)

# Insert the second pass (Yarn class name conversion in keys) and OptionsMixin fix
insert_after = lifecycle_idx
new_lines = [
    '            \n',
    '            // Second pass: also convert Yarn class names in refmap KEYS.\n',
    '            // The Forge mixin system remaps descriptor classes before looking up the refmap,\n',
    '            // so keys must use Mojang class names, not Yarn names.\n',
    '            // Build Yarn→Mojang class lookup from the obf→Yarn and obf→Mojang maps.\n',
    '            val yarnToMojangClass = mutableMapOf<String, String>()\n',
    '            for ((obf, yarnClass) in obfToYarnClass) {\n',
    '                val mojangClass = mojangClassByObf[obf] ?: continue\n',
    '                if (yarnClass != mojangClass) {\n',
    '                    yarnToMojangClass["L$yarnClass;"] = "L$mojangClass;"\n',
    '                }\n',
    '            }\n',
    '            for ((yarnRef, mojangRef) in yarnToMojangClass) {\n',
    '                convertedText = convertedText.replace(yarnRef, mojangRef)\n',
    '            }\n',
    '            refmapFile.writeText(convertedText)\n',
    '            \n',
    '            // Add missing refmap entries for mixin @Shadow fields the AP didn\'t generate.\n',
    '            var refmapText2 = refmapFile.readText()\n',
    '            if (!refmapText2.contains("OptionsMixin")) {\n',
    '                refmapText2 = refmapText2.replace(\n',
    '                    "\\"mappings\\": {",\n',
    '                    "\\"mappings\\": {\\n" +\n',
    '                    "    \\"dev/msf/friends/mixin/OptionsMixin\\": {\\"allKeys\\": \\"f_92059_:[Lnet/minecraft/client/KeyMapping;\\"},",\n',
    '                )\n',
    '                refmapFile.writeText(refmapText2)\n',
    '                logger.lifecycle("Added missing refmap entry for OptionsMixin (allKeys \u2192 f_92059_)")\n',
    '            }\n',
]

lines = lines[:insert_after + 1] + new_lines + lines[insert_after + 1:]

with open(target, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print(f"Written {len(lines)} lines with both fixes")
