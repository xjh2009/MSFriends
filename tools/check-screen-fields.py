import zipfile, struct, sys

# Check SRG for Screen.width and Screen.height
with open("tools/mcp-1171/extracted/config/joined.tsrg", "r", encoding="utf-8") as f:
    in_screen = False
    for line in f:
        line = line.rstrip("\n")
        if line.startswith("tsrg2"):
            continue
        if line.startswith("\t") or line.startswith(" "):
            if in_screen:
                if "width" in line or "height" in line:
                    print(f"  {line}")
        else:
            # class line
            if "client/gui/screens/Screen " in line:
                in_screen = True
                print(f"Found: {line}")
            else:
                in_screen = False

# Also check Yarn Screen width field name
print("\n--- Checking obf class for Screen ---")
with open("tools/mcp-1171/extracted/config/joined.tsrg", "r", encoding="utf-8") as f:
    in_screen = False
    for line in f:
        line = line.rstrip("\n")
        if line.startswith("tsrg2"):
            continue
        if line.startswith("\t") or line.startswith(" "):
            if in_screen:
                parts = line.strip().split()
                if len(parts) >= 3 and ("width" in parts[0] or "height" in parts[0] or "f_" in parts[1]):
                    print(f"  {line}")
        else:
            if "screens/Screen " in line:
                in_screen = True
                parts = line.split()
                print(f"Class: obf={parts[0]} srg={parts[1]}")
            else:
                in_screen = False

# Now check TitleScreenMixin.class in the built jar
print("\n--- Checking TitleScreenMixin.class constant pool ---")
jar_path = "build/versions-1.17.1-forge/libs/versions-1.17.1-forge-0.1.0+26.1.2-all.jar"
try:
    jar = zipfile.ZipFile(jar_path)
    data = jar.read("dev/msf/friends/mixin/MinecraftTitleMixin.class")
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)

# Parse constant pool
magic = struct.unpack(">I", data[0:4])[0]
minor = struct.unpack(">H", data[4:6])[0]
major = struct.unpack(">H", data[6:8])[0]
cp_count = struct.unpack(">H", data[8:10])[0]
print(f"Magic: {hex(magic)}, Version: {major}.{minor}, CP count: {cp_count}")

pos = 10
utf8s = {}
fieldrefs = []
natrefs = []
i = 1
while i < cp_count:
    tag = data[pos]; pos += 1
    if tag == 1:
        length = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        s = data[pos:pos+length].decode("utf-8", errors="replace")
        utf8s[i] = s; pos += length
    elif tag == 7:  # Class
        idx = struct.unpack(">H", data[pos:pos+2])[0]
        pos += 2
    elif tag == 9:  # Fieldref
        cls_idx = struct.unpack(">H", data[pos:pos+2])[0]
        nat_idx = struct.unpack(">H", data[pos+2:pos+4])[0]
        fieldrefs.append((i, cls_idx, nat_idx))
        pos += 4
    elif tag == 10:  # Methodref
        cls_idx = struct.unpack(">H", data[pos:pos+2])[0]
        nat_idx = struct.unpack(">H", data[pos+2:pos+4])[0]
        pos += 4
    elif tag == 11:  # InterfaceMethodref
        pos += 4
    elif tag == 12:  # NameAndType
        name_idx = struct.unpack(">H", data[pos:pos+2])[0]
        desc_idx = struct.unpack(">H", data[pos+2:pos+4])[0]
        natrefs.append((i, name_idx, desc_idx))
        pos += 4
    elif tag == 8:   # String
        pos += 2
    elif tag in (3, 4):  # Integer, Float
        pos += 4
    elif tag in (5, 6):  # Long, Double
        pos += 8; i += 1
    elif tag == 15:  # MethodHandle
        pos += 3
    elif tag == 16:  # MethodType
        pos += 2
    elif tag in (18, 19, 20):  # InvokeDynamic, Module, Package
        pos += 2 if tag != 18 else 4
    else:
        print(f"Unknown tag {tag} at index {i}")
        break
    i += 1

# Build NAT lookup
nat_map = {}
for nat_idx, name_idx, desc_idx in natrefs:
    nat_map[nat_idx] = (name_idx, desc_idx)

# Find width/height references
print("\nFieldref entries with width/height:")
for fr_idx, cls_idx, nat_idx in fieldrefs:
    if nat_idx in nat_map:
        name_idx, desc_idx = nat_map[nat_idx]
        name = utf8s.get(name_idx, "?")
        desc = utf8s.get(desc_idx, "?")
        if "width" in name.lower() or "height" in name.lower():
            cls_name = utf8s.get(cls_idx, "?")
            print(f"  Fieldref[{fr_idx}]: class={cls_name} name={name} desc={desc}")

# Show all UTF8 entries that contain width/height  
print("\nUTF8 entries containing 'width' or 'height':")
for k, v in utf8s.items():
    if v == "width" or v == "height" or "width" in v and len(v) < 50 or "height" in v and len(v) < 50:
        print(f"  CP[{k}] = {v}")

# Show all entries with f_96 (Screen SRG fields)
print("\nUTF8 entries containing 'f_96' (Screen SRG fields):")
for k, v in utf8s.items():
    if "f_96" in v:
        print(f"  CP[{k}] = {v}")
