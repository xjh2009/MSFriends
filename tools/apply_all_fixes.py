#!/usr/bin/env python3
"""
Apply ALL Forge 1.19.2 refmap fixes to build.gradle.kts.bak, then copy to build.gradle.kts.
Fixes:
1. val→var for convertedText
2. Yarn→Mojang class names in refmap keys (using obfToYarnClass)
3. OptionsMixin + TitleScreenMixin refmap entries for @Shadow fields
"""
import os
import shutil

bak_path = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts.bak'
target = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts'

# Always start from the ORIGINAL backup (not the already-patched one)
# First, restore the backup from the .bak.orig if it exists, otherwise use .bak as-is
bak_orig = bak_path + '.orig'
if os.path.exists(bak_orig):
    shutil.copy2(bak_orig, bak_path)
    print("Restored .bak from .bak.orig")

with open(bak_path, encoding='utf-8') as f:
    lines = f.readlines()

# Save original as .bak.orig for future runs
if not os.path.exists(bak_orig):
    shutil.copy2(bak_path, bak_orig)
    print("Saved .bak.orig backup")

# --- Fix 1: Change "val convertedText" to "var convertedText" ---
for i, line in enumerate(lines):
    if 'val convertedText = refmapText.replace' in line:
        lines[i] = line.replace('val convertedText', 'var convertedText')
        print(f"Fix 1: Line {i+1}: val->var")
        break

# --- Find insertion point: the lifecycle line after refmapFile.writeText(convertedText) ---
lifecycle_idx = None
for i, line in enumerate(lines):
    if 'logger.lifecycle("Converted Fabric refmap' in line:
        lifecycle_idx = i
        break

if lifecycle_idx is None:
    print("ERROR: Could not find insertion point")
    exit(1)

print(f"Fix 2/3: Inserting after line {lifecycle_idx + 1}")

# Build the new lines to insert
insert_lines = []

# Fix 2: Yarn->Mojang class name conversion in refmap keys
insert_lines.append('            \n')
insert_lines.append('            // Second pass: convert Yarn class names in refmap KEYS to Mojang.\n')
insert_lines.append('            // Forge mixin remaps descriptor classes before looking up the refmap,\n')
insert_lines.append('            // so keys must use Mojang names (e.g. PoseStack not MatrixStack).\n')
insert_lines.append('            val yarnToMojangClass = mutableMapOf<String, String>()\n')
insert_lines.append('            for ((obf, yarnClass) in obfToYarnClass) {\n')
insert_lines.append('                val mojangClass = mojangClassByObf[obf] ?: continue\n')
insert_lines.append('                if (yarnClass != mojangClass) {\n')
insert_lines.append('                    yarnToMojangClass["L$yarnClass;"] = "L$mojangClass;"\n')
insert_lines.append('                }\n')
insert_lines.append('            }\n')
insert_lines.append('            for ((yarnRef, mojangRef) in yarnToMojangClass) {\n')
insert_lines.append('                convertedText = convertedText.replace(yarnRef, mojangRef)\n')
insert_lines.append('            }\n')
insert_lines.append('            // Also convert intermediary class names in refmap keys\n')
insert_lines.append('            for ((intRef, mojangRef) in intClassToMojang) {\n')
insert_lines.append('                convertedText = convertedText.replace(intRef, mojangRef)\n')
insert_lines.append('            }\n')
insert_lines.append('            refmapFile.writeText(convertedText)\n')

# Fix 3: Add missing refmap entries for @Shadow fields
insert_lines.append('            \n')
insert_lines.append('            // Add missing refmap entries for @Shadow fields the AP didn\'t generate.\n')
insert_lines.append('            var refmapText2 = refmapFile.readText()\n')
insert_lines.append('            // OptionsMixin: add allKeys entry (class not in refmap at all)\n')
insert_lines.append('            if (!refmapText2.contains("OptionsMixin")) {\n')
insert_lines.append('                refmapText2 = refmapText2.replace(\n')
insert_lines.append('                    "\\"mappings\\": {",\n')
insert_lines.append('                    "\\"mappings\\": {\\"dev/msf/friends/mixin/OptionsMixin\\": {\\"allKeys\\": \\"f_92059_:[Lnet/minecraft/client/KeyMapping;\\"},",\n')
insert_lines.append('                )\n')
insert_lines.append('                logger.lifecycle("Added missing refmap entry for OptionsMixin")\n')
insert_lines.append('            }\n')
insert_lines.append('            // TitleScreenMixin: add width/height fields to existing entry\n')
insert_lines.append('            if (!refmapText2.contains("f_96543_")) {\n')
insert_lines.append('                refmapText2 = refmapText2.replace(\n')
insert_lines.append('                    "\\"TitleScreenMixin\\": {\\"",\n')
insert_lines.append('                    "\\"TitleScreenMixin\\": {\\"width\\": \\"f_96543_:I\\", \\"height\\": \\"f_96544_:I\\", ",\n')
insert_lines.append('                )\n')
insert_lines.append('                logger.lifecycle("Added width/height fields to TitleScreenMixin refmap entry")\n')
insert_lines.append('            }\n')
insert_lines.append('            refmapFile.writeText(refmapText2)\n')

lines = lines[:lifecycle_idx + 1] + insert_lines + lines[lifecycle_idx + 1:]

# Write to .bak
with open(bak_path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print(f"Wrote {len(lines)} lines to .bak")

# Copy to build.gradle.kts (locked, read-only)
tmp = target + '.tmp'
shutil.copy2(bak_path, tmp)
os.replace(tmp, target)
os.chmod(target, 0o444)  # Read-only
print(f"Copied to {target} (read-only)")
