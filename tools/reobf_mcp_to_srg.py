#!/usr/bin/env python3
"""
Direct MCP→SRG reobfuscation for Forge 1.14.4 mod jars.
Only renames method/field names that belong to Minecraft/Forge classes.
Uses tsrg mapping for per-class member lookups.
No dependency on SpecialSource.
"""
import zipfile, os, sys, tempfile, shutil

def read_u2(data, off):
    return (data[off] << 8) | data[off + 1]

def u2(val):
    return bytes([(val >> 8) & 0xFF, val & 0xFF])


def load_mappings(mcp_snapshot_zip, tsrg_path):
    """Build per-class MCP→SRG member mappings from tsrg + MCP snapshot."""
    # Step 1: Read MCP snapshot to get SRG→MCP member name lookup
    srg_to_mcp = {}
    with zipfile.ZipFile(mcp_snapshot_zip) as z:
        for csv_name in ['methods.csv', 'fields.csv']:
            raw = z.read(csv_name).decode('utf-8')
            for line in raw.strip().split('\n')[1:]:
                parts = line.split(',')
                if len(parts) >= 2 and parts[1]:
                    srg_to_mcp[parts[0]] = parts[1]

    # Step 2: Parse tsrg to build (mcp_class, mcp_member) → srg_member
    # tsrg format: obf_class mcp_class\n\tobf_member [desc] srg_member
    class_member_map = {}  # (mcp_class, mcp_member) → srg_member
    mcp_classes = set()     # set of MCP class names that have mappings
    
    with open(tsrg_path) as f:
        lines = f.readlines()
    
    current_mcp_class = None
    for line in lines:
        stripped = line.rstrip()
        if not stripped.startswith('\t'):
            parts = stripped.split()
            if len(parts) >= 2:
                current_mcp_class = parts[1]
                mcp_classes.add(current_mcp_class)
        else:
            parts = stripped.strip().split()
            if current_mcp_class and len(parts) >= 2:
                if '(' in parts[1] and len(parts) >= 3:
                    # Method: obf desc srg_name
                    srg_name = parts[2]
                elif '(' not in parts[1]:
                    # Field: obf srg_name
                    srg_name = parts[1]
                else:
                    continue
                mcp_name = srg_to_mcp.get(srg_name)
                if mcp_name and mcp_name != srg_name:
                    class_member_map[(current_mcp_class, mcp_name)] = srg_name
    
    return class_member_map, mcp_classes


def reobfuscate_class(data, class_member_map, mcp_classes):
    if len(data) < 10 or data[0:4] != b'\xca\xfe\xba\xbe':
        return None, 0

    cp_count = read_u2(data, 8)
    tags = [0] * cp_count
    utf8_strings = [None] * cp_count
    entries = [None] * cp_count
    offset = 10

    for i in range(1, cp_count):
        tag = data[offset]
        tags[i] = tag
        if tag == 1:  # UTF8
            length = read_u2(data, offset + 1)
            entries[i] = bytearray(data[offset:offset+3+length])
            utf8_strings[i] = data[offset+3:offset+3+length].decode('utf-8', errors='replace')
            offset += 3 + length
        elif tag in (7, 8, 16, 19, 20):
            entries[i] = bytearray(data[offset:offset+3])
            offset += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            entries[i] = bytearray(data[offset:offset+5])
            offset += 5
        elif tag in (5, 6):
            entries[i] = bytearray(data[offset:offset+9])
            offset += 9
        elif tag == 15:
            entries[i] = bytearray(data[offset:offset+4])
            offset += 4
        else:
            return None, 0

    rest_of_class = data[offset:]

    # Build name_idx → (class_name, member_name) for Fieldref/Methodref/InterfaceMethodref
    # These have format: tag + class_index(u2) + nat_index(u2)
    # NAT has: tag + name_index(u2) + desc_index(u2)
    # Class has: tag + name_index(u2) → points to UTF8 with class name
    
    # First, build class_index → class_name mapping
    class_names = {}  # cp_index → class_name
    for i in range(1, cp_count):
        if tags[i] == 7:  # CONSTANT_Class
            name_idx = (entries[i][1] << 8) | entries[i][2]
            if 0 < name_idx < cp_count and utf8_strings[name_idx] is not None:
                class_names[i] = utf8_strings[name_idx]
    
    # Find NAT name indices that belong to Minecraft classes
    nat_renames = {}  # nat_name_utf8_index → new_name
    
    for i in range(1, cp_count):
        if tags[i] in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            class_idx = (entries[i][1] << 8) | entries[i][2]
            nat_idx = (entries[i][3] << 8) | entries[i][4]
            
            if 0 < nat_idx < cp_count and tags[nat_idx] == 12:
                nat_name_idx = (entries[nat_idx][1] << 8) | entries[nat_idx][2]
                
                if 0 < nat_name_idx < cp_count and utf8_strings[nat_name_idx] is not None:
                    member_name = utf8_strings[nat_name_idx]
                    
                    # Check if this class is a Minecraft class
                    class_name = class_names.get(class_idx)
                    if class_name and class_name in mcp_classes:
                        key = (class_name, member_name)
                        if key in class_member_map:
                            srg_name = class_member_map[key]
                            if nat_name_idx not in nat_renames:
                                nat_renames[nat_name_idx] = srg_name
    
    renames = 0
    for name_idx, new_name in nat_renames.items():
        new_bytes = new_name.encode('utf-8')
        entries[name_idx] = bytearray(b'\x01' + u2(len(new_bytes)) + new_bytes)
        renames += 1

    if renames == 0:
        return None, 0

    # Rebuild class file
    out = bytearray()
    out.extend(data[0:8])
    out.extend(u2(cp_count))
    for i in range(1, cp_count):
        if entries[i] is not None:
            out.extend(entries[i])
    out.extend(rest_of_class)
    return bytes(out), renames


def reobfuscate_jar(input_jar, output_jar, mcp_snapshot_zip, tsrg_path):
    class_member_map, mcp_classes = load_mappings(mcp_snapshot_zip, tsrg_path)
    print('Loaded %d class-member mappings for %d Minecraft classes' % (len(class_member_map), len(mcp_classes)))
    total_classes = 0
    total_renames = 0
    errors = 0
    tmp = tempfile.mkdtemp(prefix='reobf_')
    try:
        with zipfile.ZipFile(input_jar, 'r') as zin:
            zin.extractall(tmp)
        for root, dirs, files in os.walk(tmp):
            for f in files:
                if f.endswith('.class'):
                    fp = os.path.join(root, f)
                    with open(fp, 'rb') as fh:
                        data = fh.read()
                    try:
                        result, renames = reobfuscate_class(data, class_member_map, mcp_classes)
                        if result is not None:
                            with open(fp, 'wb') as fh:
                                fh.write(result)
                            total_classes += 1
                            total_renames += renames
                    except Exception as e:
                        errors += 1
                        if errors <= 5:
                            print('  Warning: %s in %s' % (e, f))
        if os.path.exists(output_jar):
            os.remove(output_jar)
        with zipfile.ZipFile(output_jar, 'w', zipfile.ZIP_DEFLATED) as zout:
            for root, dirs, files in os.walk(tmp):
                for f in files:
                    fp = os.path.join(root, f)
                    arcname = os.path.relpath(fp, tmp)
                    zout.write(fp, arcname)
    finally:
        shutil.rmtree(tmp)
    size_mb = os.path.getsize(output_jar) / (1024*1024)
    print('Reobfuscated %d classes, %d renames, %d warnings' % (total_classes, total_renames, errors))
    print('Output: %s (%.2f MB)' % (output_jar, size_mb))


if __name__ == '__main__':
    if len(sys.argv) < 3:
        print('Usage: %s <input.jar> <output.jar> [mcp_snapshot.zip] [tsrg_file]' % sys.argv[0])
        sys.exit(1)
    MCP_SNAPSHOT = sys.argv[3] if len(sys.argv) > 3 else r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_snapshot\20190601-1.14.2\mcp_snapshot-20190601-1.14.2.zip'
    TSRG = sys.argv[4] if len(sys.argv) > 4 else r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.14.4-20190829.143755\joined\data\mappings\joined.tsrg'
    reobfuscate_jar(sys.argv[1], sys.argv[2], MCP_SNAPSHOT, TSRG)
