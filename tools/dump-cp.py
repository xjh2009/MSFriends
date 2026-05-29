import zipfile, struct, sys

jar = zipfile.ZipFile("build/versions-1.17.1-forge/libs/versions-1.17.1-forge-0.1.0+26.1.2-all.jar")
data = jar.read("dev/msf/friends/mixin/MinecraftTitleMixin.class")

# Parse constant pool
pos = 10  # 4 magic + 2 minor + 2 major
cp_count = struct.unpack(">H", data[pos:pos+2])[0]
pos += 2
utf8s = {}
i = 1
while i < cp_count:
    tag = data[pos]; pos += 1
    if tag == 1:
        length = struct.unpack(">H", data[pos:pos+2])[0]; pos += 2
        s = data[pos:pos+length].decode("utf-8", errors="replace")
        utf8s[i] = s; pos += length
    elif tag in (3, 4): pos += 4
    elif tag in (5, 6): pos += 8; i += 1
    elif tag in (7, 8): pos += 2
    elif tag in (9, 10, 11, 18): pos += 4
    elif tag == 12: pos += 4
    elif tag == 15: pos += 3
    elif tag in (16, 19, 20): pos += 2
    else:
        print(f"Unknown tag {tag} at index {i}", file=sys.stderr)
        break
    i += 1

print(f"Total CP entries: {cp_count}")
for k, v in utf8s.items():
    if "width" in v.lower() or "height" in v.lower() or "TitleScreen" in v or "Screen" in v:
        print(f"  CP[{k}] = {v}")
