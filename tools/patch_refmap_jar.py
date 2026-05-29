#!/usr/bin/env python3
"""
Post-build patch: fix refmap entries AND multi-overload method names in the Forge fat jar.
"""
import zipfile
import json
import struct
import sys
import os

jar_path = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\versions-1.19.2-forge\libs\versions-1.19.2-forge-0.1.0+26.1.2-all.jar'

refmap_name = 'versions-1.19.2-common-refmap.json'

# Multi-overload method names that patchClassConstantPool can't handle.
# These are unique-enough names that can be safely replaced by simple UTF8 matching.
# Each entry maps Yarn name -> SRG name (the correct overload for our usage).
OVERLOADED_METHODS = {
    'translatable': 'm_237113_',
    'getMessage': 'm_5646_',
    'BOOLEAN': 'f_231471_',
    'toButton': 'm_231549_',
}

# Fields inherited from MC classes that our code references via dev/msf/ class owners.
# These can't be handled by patchClassConstantPool safety check (which only allows MC class owners).
OVERLOADED_FIELDS = {
    'alpha': 'f_93625_',
    'x': 'f_96545_',
    'y': 'f_96546_',
    'width': 'f_96543_',
    'height': 'f_96544_',
}

def patch_overloaded_methods(data, replacements):
    """Replace overloaded method names in constant pool UTF8 entries.
    Only replaces names that are referenced by MethodRef/FieldRef entries
    pointing to MC classes (net/minecraft/**), not java/lang/** etc."""
    if len(data) < 10:
        return data

    header = bytearray(data[:8])  # magic(4) + minor(2) + major(2)
    pos = 8
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2

    # First pass: collect all constant pool entries
    entries = []  # list of (tag, raw_bytes)
    utf8_strings = {}  # index -> string
    class_names = {}   # class_cp_idx -> utf8_idx
    nats = {}          # nat_cp_idx -> (name_utf8_idx, desc_utf8_idx)
    method_refs = {}   # method_cp_idx -> (class_cp_idx, nat_cp_idx)

    scan_pos = pos
    i = 1
    while i < cp_count and scan_pos < len(data):
        tag = data[scan_pos]
        scan_pos += 1
        if tag == 1:  # UTF8
            length = struct.unpack('>H', data[scan_pos:scan_pos+2])[0]
            raw = data[scan_pos:scan_pos+2+length]
            try:
                s = data[scan_pos+2:scan_pos+2+length].decode('utf-8')
                utf8_strings[i] = s
            except UnicodeDecodeError:
                pass
            entries.append((tag, raw))
            scan_pos += 2 + length
        elif tag in (3, 4):
            entries.append((tag, data[scan_pos:scan_pos+4]))
            scan_pos += 4
        elif tag in (5, 6):
            entries.append((tag, data[scan_pos:scan_pos+8]))
            scan_pos += 8
            i += 1
            entries.append((0, b''))  # placeholder
        elif tag == 7:
            raw = data[scan_pos:scan_pos+2]
            class_names[i] = struct.unpack('>H', raw)[0]
            entries.append((tag, raw))
            scan_pos += 2
        elif tag == 8:
            entries.append((tag, data[scan_pos:scan_pos+2]))
            scan_pos += 2
        elif tag in (9, 10, 11):
            raw = data[scan_pos:scan_pos+4]
            c = struct.unpack('>H', raw[0:2])[0]
            n = struct.unpack('>H', raw[2:4])[0]
            method_refs[i] = (c, n)
            entries.append((tag, raw))
            scan_pos += 4
        elif tag == 12:
            raw = data[scan_pos:scan_pos+4]
            name_idx = struct.unpack('>H', raw[0:2])[0]
            desc_idx = struct.unpack('>H', raw[2:4])[0]
            nats[i] = (name_idx, desc_idx)
            entries.append((tag, raw))
            scan_pos += 4
        elif tag == 15:
            entries.append((tag, data[scan_pos:scan_pos+3]))
            scan_pos += 3
        elif tag == 16:
            entries.append((tag, data[scan_pos:scan_pos+2]))
            scan_pos += 2
        elif tag in (17, 18):
            entries.append((tag, data[scan_pos:scan_pos+4]))
            scan_pos += 4
        elif tag in (19, 20):
            entries.append((tag, data[scan_pos:scan_pos+2]))
            scan_pos += 2
        else:
            break
        i += 1

    # Build set of UTF8 indices that are safe to replace.
    # A UTF8 entry is safe if NO MethodRef/FieldRef referencing it via NameAndType
    # points to java/** classes (like Throwable.getMessage).
    # This allows: net/minecraft/**, com/mojang/**, and our own dev/msf/** classes.
    safe_to_replace = set()
    for utf8_idx, utf8_str in utf8_strings.items():
        if utf8_str not in replacements:
            continue
        is_safe = True
        for nat_cp, (name_idx, desc_idx) in nats.items():
            if name_idx != utf8_idx:
                continue
            for mr_cp, (class_cp, nat_cp2) in method_refs.items():
                if nat_cp2 != nat_cp:
                    continue
                class_utf8_idx = class_names.get(class_cp, 0)
                class_name = utf8_strings.get(class_utf8_idx, '')
                if class_name.startswith('java/'):
                    is_safe = False
                    break
            if not is_safe:
                break
        if is_safe:
            safe_to_replace.add(utf8_idx)

    # Apply replacements only to safe entries
    changed = False
    for idx, (tag, raw) in enumerate(entries):
        cp_idx = idx + 1  # 1-based
        if tag != 1 or cp_idx not in safe_to_replace:
            continue
        s = utf8_strings.get(cp_idx)
        if s and s in replacements:
            new_s = replacements[s]
            new_bytes = new_s.encode('utf-8')
            entries[idx] = (1, struct.pack('>H', len(new_bytes)) + new_bytes)
            changed = True

    if not changed:
        return data

    # Rebuild constant pool
    new_cp = bytearray()
    for tag, raw in entries:
        if tag == 0:
            # Long/Double placeholder - no bytes needed, just skip
            pass
        else:
            new_cp.append(tag)
            new_cp.extend(raw)

    rest = data[scan_pos:]
    result = bytearray()
    result.extend(header)
    result.extend(struct.pack('>H', cp_count))
    result.extend(new_cp)
    result.extend(rest)

    return bytes(result)

def read_u2(data, offset):
    return struct.unpack('>H', data[offset:offset+2])[0]

def patch_class_constant_pool(data, replacements):
    """Replace bare UTF8 strings in a class constant pool.
    replacements: dict of {old_name: new_name}
    Returns patched bytes if any changes were made, else original.
    """
    if len(data) < 10:
        return data

    result = bytearray(data)
    pos = 4 + 2 + 2  # magic + minor + major
    cp_count = read_u2(data, pos)
    pos += 2

    changed = False
    # First pass: collect all UTF8 entries and their positions
    utf8_entries = []  # (cp_index, data_pos, length)
    scan_pos = pos
    i = 1
    while i < cp_count and scan_pos < len(data):
        tag = data[scan_pos]
        scan_pos += 1
        if tag == 1:  # UTF8
            length = read_u2(data, scan_pos)
            data_pos = scan_pos + 2
            utf8_entries.append((i, data_pos, length))
            scan_pos += length + 2
        elif tag in (3, 4): scan_pos += 4
        elif tag in (5, 6): scan_pos += 8; i += 1
        elif tag == 7: scan_pos += 2
        elif tag == 8: scan_pos += 2
        elif tag in (9, 10, 11, 12): scan_pos += 4
        elif tag == 15: scan_pos += 3
        elif tag == 16: scan_pos += 2
        elif tag in (17, 18): scan_pos += 4
        elif tag in (19, 20): scan_pos += 2
        else: break
        i += 1

    # For each UTF8 entry, check if it matches a replacement
    for cp_idx, data_pos, length in utf8_entries:
        try:
            s = data[data_pos:data_pos+length].decode('utf-8')
        except UnicodeDecodeError:
            continue
        if s in replacements:
            new_s = replacements[s]
            new_bytes = new_s.encode('utf-8')
            if len(new_bytes) == len(new_bytes):  # always true, but check length
                # Replace in-place if same length
                if len(new_bytes) == length:
                    result[data_pos:data_pos+length] = new_bytes
                    changed = True
                else:
                    # Different length - skip (too complex for post-build patch)
                    pass

    return bytes(result) if changed else data

# Read jar
with zipfile.ZipFile(jar_path, 'r') as zin:
    refmap_raw = zin.read(refmap_name).decode('utf-8')
    all_files = {}
    for name in zin.namelist():
        all_files[name] = zin.read(name)

# Parse and patch refmap
refmap = json.loads(refmap_raw)
mappings = refmap.get('mappings', {})

# Fix 1: OptionsMixin - add allKeys field entry if missing
if 'dev/msf/friends/mixin/OptionsMixin' not in mappings:
    mappings['dev/msf/friends/mixin/OptionsMixin'] = {
        'allKeys': 'f_92059_:[Lnet/minecraft/client/KeyMapping;'
    }
    print('Added OptionsMixin refmap entry')

# Fix 2: TitleScreenMixin - add width/height fields if missing
if 'dev/msf/friends/mixin/TitleScreenMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/TitleScreenMixin']
    if 'width' not in entry:
        entry['width'] = 'f_96543_:I'
        entry['height'] = 'f_96544_:I'
        new_entry = {'width': entry['width'], 'height': entry['height']}
        for k, v in entry.items():
            if k not in ('width', 'height'):
                new_entry[k] = v
        mappings['dev/msf/friends/mixin/TitleScreenMixin'] = new_entry
        print('Added width/height to TitleScreenMixin refmap entry')

# Fix 3: ClientPacketListenerMixin - add getConnection method entry if missing
if 'dev/msf/friends/mixin/ClientPacketListenerMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ClientPacketListenerMixin']
    if 'getConnection' not in entry:
        entry['getConnection'] = 'Lnet/minecraft/client/multiplayer/ClientPacketListener;m_6198_()Lnet/minecraft/network/Connection;'
        print('Added getConnection to ClientPacketListenerMixin refmap entry')

# Fix 4: ServerLoginMixin - add shadow fields/methods if missing
if 'dev/msf/friends/mixin/ServerLoginMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ServerLoginMixin']
    if 'server' not in entry:
        entry['server'] = 'f_10018_:Lnet/minecraft/server/MinecraftServer;'
        print('Added server to ServerLoginMixin')
    if 'connection' not in entry:
        entry['connection'] = 'f_10013_:Lnet/minecraft/network/Connection;'
        print('Added connection to ServerLoginMixin')
    if 'profile' not in entry:
        entry['profile'] = 'f_10021_:Lcom/mojang/authlib/GameProfile;'
        print('Added profile to ServerLoginMixin')
    if 'acceptPlayer' not in entry:
        entry['acceptPlayer'] = 'm_10055_()V'
        print('Added acceptPlayer to ServerLoginMixin')

# Fix 5: ClientLoginMixin - add connection field if missing
if 'dev/msf/friends/mixin/ClientLoginMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ClientLoginMixin']
    if 'connection' not in entry:
        entry['connection'] = 'f_104522_:Lnet/minecraft/network/Connection;'
        print('Added connection to ClientLoginMixin')

# Fix 6: ShareToLanScreenMixin - add parent field if missing
if 'dev/msf/friends/mixin/ShareToLanScreenMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ShareToLanScreenMixin']
    if 'parent' not in entry:
        entry['parent'] = 'f_96643_:Lnet/minecraft/client/gui/screens/Screen;'
        print('Added parent to ShareToLanScreenMixin')

refmap_bytes = json.dumps(refmap, indent=2).encode('utf-8')

# Fix 7: Patch multi-overload method names in ALL class constant pools
patched_count = 0
for name, data in list(all_files.items()):
    if not name.endswith('.class'):
        continue
    patched = patch_overloaded_methods(data, OVERLOADED_METHODS)
    if patched is not data:
        all_files[name] = patched
        patched_count += 1
if patched_count:
    print(f'Patched {patched_count} class files with overloaded method names')

# Rewrite jar
all_files[refmap_name] = refmap_bytes
tmp_path = jar_path + '.tmp'
with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, data in all_files.items():
        zout.writestr(name, data)

os.replace(tmp_path, jar_path)
print(f'Patched jar: {jar_path}')
print(f'  Refmap: {len(refmap_raw)} -> {len(refmap_bytes)} bytes')
