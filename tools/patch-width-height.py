import zipfile, struct, shutil, sys
sys.stdout.reconfigure(encoding='utf-8')

jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
backup = jar_path + '.bak'
shutil.copy2(jar_path, backup)

# Map of class -> {yarn_field: srg_field}
fixes = {
    'dev/msf/friends/mixin/TitleScreenMixin.class': {'width': 'f_96543_', 'height': 'f_96544_'},
    'dev/msf/friends/screen/FriendsScreen.class': {'width': 'f_96543_', 'height': 'f_96544_'},
    'dev/msf/friends/screen/P2PConnectScreen.class': {'width': 'f_96543_', 'height': 'f_96544_'},
}

# Read all entries
with zipfile.ZipFile(jar_path, 'r') as zin:
    entries = {}
    for name in zin.namelist():
        entries[name] = zin.read(name)

# Patch classes
patched = 0
for class_name, field_map in fixes.items():
    if class_name not in entries:
        print(f'  {class_name} not found in jar')
        continue
    data = bytearray(entries[class_name])
    pos = 8  # 4 magic + 2 minor + 2 major
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2

    # Parse constant pool to find UTF8 entries
    utf8_entries = []  # (cp_index, length_field_pos, data_pos, length)
    i = 1
    scan_pos = pos
    while i < cp_count:
        tag = data[scan_pos]; scan_pos += 1
        if tag == 1:  # UTF8
            length = struct.unpack('>H', data[scan_pos:scan_pos+2])[0]
            utf8_entries.append((i, scan_pos, scan_pos + 2, length))
            scan_pos += 2 + length
        elif tag in (3,4): scan_pos += 4
        elif tag in (5,6): scan_pos += 8; i += 1
        elif tag in (7,8): scan_pos += 2
        elif tag in (9,10,11,18): scan_pos += 4
        elif tag == 12: scan_pos += 4
        elif tag == 15: scan_pos += 3
        elif tag in (16,19,20): scan_pos += 2
        else: break
        i += 1

    # Find and patch matching entries (backwards to avoid offset shifts)
    patches = []
    for cp_idx, length_field_pos, data_pos, length in utf8_entries:
        s = data[data_pos:data_pos+length].decode('utf-8', errors='replace')
        if s in field_map:
            new_s = field_map[s]
            print(f'  {class_name}: CP[{cp_idx}] "{s}" -> "{new_s}"')
            patches.append((length_field_pos, data_pos, length, new_s))

    if not patches:
        print(f'  {class_name}: no matching fields found')
        continue

    # Apply patches backwards
    patches.sort(key=lambda x: x[1], reverse=True)
    for length_field_pos, data_pos, length, new_s in patches:
        new_bytes = new_s.encode('utf-8')
        new_len = len(new_bytes)
        # Replace length field
        data[length_field_pos] = (new_len >> 8) & 0xFF
        data[length_field_pos + 1] = new_len & 0xFF
        # Replace data
        data[data_pos:data_pos+length] = new_bytes
    
    entries[class_name] = bytes(data)
    patched += 1

# Write new jar
with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, data in entries.items():
        zout.writestr(name, data)

print(f'Patched {patched} classes')
print(f'Backup: {backup}')
