"""
Proper constant pool rewriter for Forge 1.15.2.
Correctly handles Long/Double double-slot entries.
Only replaces exact UTF8 matches in the constant pool.
"""
import os, sys, json, zipfile, struct, re

def parse_yarn(path):
    int_to_off = {}
    off_to_named = {}
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if not parts or parts[0] == 'v1': continue
            if parts[0] == 'CLASS' and len(parts) >= 4:
                int_to_off[parts[2]] = parts[1]
                off_to_named[parts[1]] = parts[3]
    return int_to_off, off_to_named

def parse_srg(path):
    off_to_srg = {}
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            s = line.rstrip('\n')
            if s.startswith('tsrg2') or not s: continue
            if not s.startswith('\t'):
                parts = s.split()
                if len(parts) >= 2:
                    off_to_srg[parts[0]] = parts[1]
    return off_to_srg

def remap_refmap_entry(value, int_class_map):
    new_value = value
    def replacer(m):
        cls = m.group(1)
        return 'L' + int_class_map.get(cls, cls) + ';'
    new_value = re.sub(r'L([^;]+);', replacer, new_value)
    for old, new in sorted(int_class_map.items(), key=lambda kv: len(kv[0]), reverse=True):
        if old in new_value:
            new_value = new_value.replace(old, new)
    return new_value

def rewrite_class_cp(data, replacements, exact_replacements=None):
    """
    Rewrite a Java class file's constant pool, replacing UTF8 strings.
    replacements: dict of old_str -> new_str (substring match, for class names)
    exact_replacements: dict of old_str -> new_str (exact UTF8 match, for field/method names)
    Returns new class file bytes, or original if no changes needed.
    """
    if len(data) < 10:
        return data
    magic = struct.unpack('>I', data[0:4])[0]
    if magic != 0xCAFEBABE:
        return data

    # Parse header
    minor = struct.unpack('>H', data[4:6])[0]
    major = struct.unpack('>H', data[6:8])[0]
    cp_count = struct.unpack('>H', data[8:10])[0]

    # Downgrade to Java 8 if needed
    if major > 52:
        major = 52
        minor = 0

    # Parse constant pool entries
    idx = 10
    entries = []  # list of (tag, raw_bytes, extra_slot)
    slot = 1
    while slot < cp_count:
        if idx >= len(data):
            entries.append((0, b'', False))
            slot += 1
            continue
        tag = data[idx]
        if tag == 1:  # UTF8
            length = struct.unpack('>H', data[idx+1:idx+3])[0]
            raw = bytes(data[idx:idx+3+length])
            entries.append((tag, raw, False))
            idx += 3 + length
            slot += 1
        elif tag in (7, 8, 16, 19, 20):
            entries.append((tag, bytes(data[idx:idx+3]), False))
            idx += 3
            slot += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            entries.append((tag, bytes(data[idx:idx+5]), False))
            idx += 5
            slot += 1
        elif tag in (5, 6):  # Long, Double — takes 2 CP slots
            entries.append((tag, bytes(data[idx:idx+9]), True))
            entries.append(None)  # phantom slot
            idx += 9
            slot += 2
        elif tag == 15:
            entries.append((tag, bytes(data[idx:idx+4]), False))
            idx += 4
            slot += 1
        else:
            # Unknown tag — bail
            return data

    cp_end = idx

    # Determine if this is an MC class (to decide whether to apply exact field/method replacements)
    is_mc_class = False
    for entry in entries:
        if entry is not None and entry[0] == 1:
            length = struct.unpack('>H', entry[1][1:3])[0]
            s = entry[1][3:3+length].decode('utf-8', errors='replace')
            if 'net/minecraft/' in s or 'com/mojang/' in s:
                is_mc_class = True
                break

    # Check if this class is an MC class (contains net/minecraft/ references)
    is_mc_class = any(
        e is not None and e[0] == 1 and b'net/minecraft/' in e[1]
        for e in entries
    )

    # Apply replacements to UTF8 entries
    changed = False
    new_entries = []
    for entry in entries:
        if entry is None:
            new_entries.append(None)
            continue
        tag, raw, extra = entry
        if tag == 1:
            length = struct.unpack('>H', raw[1:3])[0]
            s = raw[3:3+length].decode('utf-8', errors='replace')
            ns = s
            # Only apply substring class name replacements to entries containing '/'
            if '/' in s:
                for old, new in replacements.items():
                    if old in ns:
                        ns = ns.replace(old, new)
            # Apply exact match field/method names only in MC classes
            if ns == s and is_mc_class and exact_replacements and s in exact_replacements:
                ns = exact_replacements[s]
            # Apply exact field/method name replacements only for MC classes
            if exact_replacements and is_mc_class and ns == s and s in exact_replacements:
                ns = exact_replacements[s]
            if ns != s:
                nb = ns.encode('utf-8')
                new_raw = struct.pack('>BH', 1, len(nb)) + nb
                new_entries.append((tag, new_raw, extra))
                changed = True
            else:
                new_entries.append(entry)
        else:
            new_entries.append(entry)

    if not changed:
        # Just downgrade version if needed
        if struct.unpack('>H', data[6:8])[0] > 52:
            data = bytearray(data)
            struct.pack_into('>HH', data, 4, 0, 52)
            return bytes(data)
        return data

    # Rebuild class file
    header = struct.pack('>IHH', 0xCAFEBABE, minor, major)
    cp_header = struct.pack('>H', cp_count)
    cp_bytes = b''
    for entry in new_entries:
        if entry is not None:
            cp_bytes += entry[1]
    rest = data[cp_end:]

    return header + cp_header + cp_bytes + rest


def main():
    jar_path = sys.argv[1]
    home = os.path.expanduser('~')
    yarn_path = os.path.join(home, '.gradle', 'caches', 'fabric-loom', '1.15.2',
        'net.fabricmc.yarn.1_15_2.1.15.2+build.17', 'mappings-base.tiny')
    srg_path = os.path.join(home, '.gradle', 'caches', 'minecraftforge', 'forgegradle',
        'mavenizer', 'caches', 'mcp', 'de', 'oceanlabs', 'mcp', 'mcp_config',
        '1.15.2-20200515.085601', 'client', 'data', 'mappings', 'joined.tsrg')

    print("Parsing mappings...")
    int_to_off, off_to_named = parse_yarn(yarn_path)
    off_to_srg = parse_srg(srg_path)

    int_to_srg_class = {}
    for inter, off in int_to_off.items():
        srg = off_to_srg.get(off)
        if srg:
            int_to_srg_class[inter] = srg

    # Build named→SRG for bytecode rewriting, sorted longest-first
    named_to_srg = {}
    for off, named in off_to_named.items():
        srg = off_to_srg.get(off)
        if srg and named != srg:
            named_to_srg[named] = srg

    # Sort by key length descending to replace longest names first
    # Build named->srg for fields (Yarn named name -> SRG name)
    off_fld_named = {}
    off_meth_named = {}
    with open(yarn_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if parts[0] == 'FIELD' and len(parts) >= 6:
                off_fld_named[(parts[1], parts[3])] = parts[5]
            elif parts[0] == 'METHOD' and len(parts) >= 6:
                off_meth_named[(parts[1], parts[3], parts[2])] = parts[5]

    off_fld_srg = {}
    off_meth_srg = {}
    cur_off = ''
    with open(srg_path, 'r', encoding='utf-8') as f:
        for line in f:
            s = line.rstrip('\n')
            if s.startswith('tsrg2') or not s: continue
            if s.startswith('\t'):
                parts = s.strip().split()
                if len(parts) >= 3 and '(' in parts[1]:
                    off_meth_srg[(cur_off, parts[0], parts[1])] = parts[2]
                elif len(parts) >= 2:
                    off_fld_srg[(cur_off, parts[0])] = parts[1]
            else:
                parts = s.split()
                if len(parts) >= 2:
                    cur_off = parts[0]

    named_fld_to_srg = {}
    int_fld_to_srg = {}  # intermediary field name -> SRG name
    for (off_cls, off_fld_name), named_name in off_fld_named.items():
        srg_name = off_fld_srg.get((off_cls, off_fld_name))
        if srg_name and named_name != srg_name and len(named_name) >= 6:
            named_fld_to_srg[named_name] = srg_name
        # Also map intermediary name -> SRG (for unmapped fields where named==intermediary)
        # Parse intermediary name from Yarn tiny: parts[4] is intermediary
        # We need to re-parse to get it
    # Re-parse to get intermediary field names
    with open(yarn_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if parts[0] == 'FIELD' and len(parts) >= 6:
                off_cls_f, off_name_f, int_name_f = parts[1], parts[3], parts[4]
                srg_name_f = off_fld_srg.get((off_cls_f, off_name_f))
                if srg_name_f and int_name_f != srg_name_f and int_name_f not in named_fld_to_srg:
                    int_fld_to_srg[int_name_f] = srg_name_f

    named_meth_to_srg = {}
    int_meth_to_srg = {}  # intermediary method name -> SRG name
    for (off_cls, off_name, off_desc), named_name in off_meth_named.items():
        srg_name = off_meth_srg.get((off_cls, off_name, off_desc))
        if srg_name and named_name != srg_name and len(named_name) >= 6:
            named_meth_to_srg[named_name] = srg_name
    # Re-parse to get intermediary method names
    with open(yarn_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if parts[0] == 'METHOD' and len(parts) >= 6:
                off_cls_m, off_name_m, off_desc_m, int_name_m = parts[1], parts[3], parts[2], parts[4]
                srg_name_m = off_meth_srg.get((off_cls_m, off_name_m, off_desc_m))
                if srg_name_m and int_name_m != srg_name_m and int_name_m not in named_meth_to_srg:
                    int_meth_to_srg[int_name_m] = srg_name_m

    # Merge ALL field/method mappings for exact replacements
    all_exact = {}
    all_exact.update(named_fld_to_srg)
    all_exact.update(int_fld_to_srg)
    all_exact.update(named_meth_to_srg)
    all_exact.update(int_meth_to_srg)

    # For BYTECODE: only class name replacements (contain '/')
    # Field/method name replacements would break annotations/descriptors
    sorted_named_to_srg = dict(sorted(named_to_srg.items(), key=lambda kv: len(kv[0]), reverse=True))

    # For REFPARCE: class + field + method name replacements
    all_refmap = {}
    all_refmap.update(int_to_srg_class)
    # Add field/method intermediary->srg for refmap
    for (off_cls, off_fld_name), named_name in off_fld_named.items():
        srg_name = off_fld_srg.get((off_cls, off_fld_name))
        if srg_name:
            int_name = [k for k,v in off_to_named.items() if v == named_name]
            # Use intermediary field names for refmap
    # Build int_meth_to_srg and int_fld_to_srg for refmap
    int_fld_to_srg = {}
    for (off_cls, off_fld_name), named_name in off_fld_named.items():
        srg_name = off_fld_srg.get((off_cls, off_fld_name))
        if srg_name and named_name != srg_name:
            int_fld_to_srg[named_name] = srg_name
    int_meth_to_srg = {}
    for (off_cls, off_name, off_desc), named_name in off_meth_named.items():
        srg_name = off_meth_srg.get((off_cls, off_name, off_desc))
        if srg_name and named_name != srg_name:
            int_meth_to_srg[named_name] = srg_name

    print(f"  {len(int_to_srg_class)} int->srg, {len(named_to_srg)} class, {len(named_fld_to_srg)} field, {len(named_meth_to_srg)} method")

    print(f"Processing: {jar_path}")
    tmp = jar_path + '.tmp'
    refmap_count = 0
    class_count = 0

    with zipfile.ZipFile(jar_path, 'r') as zin:
        with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)

                if item.filename.endswith('refmap.json'):
                    try:
                        obj = json.loads(data.decode('utf-8'))
                        for section in ('mappings', 'data'):
                            if section not in obj or not isinstance(obj[section], dict): continue
                            for mixin_name, mmap in obj[section].items():
                                if isinstance(mmap, dict):
                                    for k, v in mmap.items():
                                        if isinstance(v, str):
                                            nv = remap_refmap_entry(v, int_to_srg_class)
                                            if nv != v:
                                                mmap[k] = nv
                                                refmap_count += 1
                        data = json.dumps(obj, indent=2).encode('utf-8')
                    except Exception as e:
                        print(f"  Refmap error: {e}")

                elif item.filename.endswith('.class'):
                    try:
                        new_data = rewrite_class_cp(data, sorted_named_to_srg, all_exact)
                        if new_data != data:
                            class_count += 1
                        data = new_data
                    except Exception as e:
                        pass  # Skip corrupted

                # Rename refmap file
                new_name = item.filename
                if 'common-refmap.json' in item.filename:
                    new_name = 'msf-friends-refmap.json'

                zout.writestr(new_name, data)

    os.replace(tmp, jar_path)
    print(f"Done: {refmap_count} refmap entries, {class_count} classes rewritten")

if __name__ == '__main__':
    main()
