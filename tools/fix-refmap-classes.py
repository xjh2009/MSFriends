#!/usr/bin/env python3
"""Fix Forge 1.19.2 refmap: convert intermediary class_XXX names to Mojang names.
Uses Yarn tiny file + ProGuard mapping to bridge intermediary→Mojang.
"""
import json, re, os, sys, zipfile, shutil, tempfile

PROJECT = r"C:\Users\xjh37\Desktop\MSF\msf-friends-multi"
YARN_FILE = os.path.expandvars(
    r"%USERPROFILE%\.gradle\caches\fabric-loom\1.19.2\net.fabricmc.yarn.1_19_2.1.19.2+build.28\mappings-base.tiny"
)
PROGUARD_FILE = os.path.join(PROJECT, "build", "mojang-client.txt")
JAR_FILE = os.path.join(PROJECT, "build", "versions-1.19.2-forge", "libs", "versions-1.19.2-forge-0.1.0+26.1.2-all.jar")
REFMAP_NAME = "versions-1.19.2-common-refmap.json"

# 1. Parse Yarn tiny: intermediary class name → obf (official)
print("Parsing Yarn tiny file...")
int_to_obf = {}
with open(YARN_FILE, encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "CLASS" and len(parts) >= 3:
            int_to_obf[parts[2]] = parts[1]  # intermediary → obf
print(f"  intermediary→obf class map: {len(int_to_obf)} entries")

# 2. Parse ProGuard: obf → Mojang
print("Parsing ProGuard mapping...")
obf_to_mojang = {}
with open(PROGUARD_FILE, encoding="utf-8") as f:
    for line in f:
        m = re.match(r'^(\S+)\s+->\s+(\S+):', line)
        if m:
            mojang = m.group(1).replace('.', '/')
            obf = m.group(2)
            obf_to_mojang[obf] = mojang
print(f"  obf→Mojang class map: {len(obf_to_mojang)} entries")

# 3. Build intermediary→Mojang bridge
int_to_mojang = {}
for int_name, obf_name in int_to_obf.items():
    mojang = obf_to_mojang.get(obf_name)
    if mojang:
        int_to_mojang[int_name] = mojang
print(f"  intermediary→Mojang bridge: {len(int_to_mojang)} entries")

# Show some samples
samples = list(int_to_mojang.items())[:5]
for k, v in samples:
    print(f"    {k} -> {v}")

# 4. Fix refmap
print(f"\nOpening jar: {JAR_FILE}")
tmpdir = tempfile.mkdtemp(prefix="msf-refmap-")
refmap_path_in_jar = None

with zipfile.ZipFile(JAR_FILE, 'r') as zin:
    # Find the refmap
    for name in zin.namelist():
        if name.endswith("refmap.json"):
            refmap_path_in_jar = name
            break
    
    if not refmap_path_in_jar:
        print("ERROR: No refmap found in jar!")
        sys.exit(1)
    
    print(f"  Found refmap: {refmap_path_in_jar}")
    refmap_data = json.loads(zin.read(refmap_path_in_jar))

# 5. Convert class descriptors in refmap
mappings = refmap_data.get("mappings", {})
converted_count = 0

def replace_int_classes(text):
    """Replace all Lintermediary; patterns with LMojang; patterns."""
    global converted_count
    result = text
    for int_name, mojang_name in int_to_mojang.items():
        old = "L" + int_name + ";"
        new = "L" + mojang_name + ";"
        if old in result:
            result = result.replace(old, new)
            converted_count += 1
    return result

for class_name, method_map in mappings.items():
    for method_key, descriptor in list(method_map.items()):
        if isinstance(descriptor, str) and "class_" in descriptor:
            new_descriptor = replace_int_classes(descriptor)
            if new_descriptor != descriptor:
                method_map[method_key] = new_descriptor
                print(f"  {class_name}.{method_key}: {descriptor} -> {new_descriptor}")

print(f"\nTotal replacements: {converted_count}")

# Also fix the "data" section if it exists
data = refmap_data.get("data", {})
for data_key, data_val in data.items():
    if isinstance(data_val, dict):
        for cls, mmap in data_val.items():
            if isinstance(mmap, dict):
                for mk, desc in list(mmap.items()):
                    if isinstance(desc, str) and "class_" in desc:
                        new_desc = replace_int_classes(desc)
                        if new_desc != desc:
                            mmap[mk] = new_desc

# 6. Write back to jar
print("Writing updated refmap back to jar...")
updated_refmap = json.dumps(refmap_data, indent=2, ensure_ascii=False)

# Create new jar with updated refmap
new_jar = JAR_FILE + ".new"
with zipfile.ZipFile(JAR_FILE, 'r') as zin:
    with zipfile.ZipFile(new_jar, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == refmap_path_in_jar:
                zout.writestr(item, updated_refmap.encode('utf-8'))
            else:
                zout.writestr(item, zin.read(item.filename))

# Replace original
shutil.move(new_jar, JAR_FILE)
print(f"Done! Updated jar: {JAR_FILE}")
print(f"Jar size: {os.path.getsize(JAR_FILE)} bytes")
