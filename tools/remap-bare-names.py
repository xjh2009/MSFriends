"""
Comprehensive Yarn→SRG bare name remapper for Forge 1.17.1.
Uses Yarn tiny + SRG TSRG2 + Mojang ProGuard to build a complete mapping table,
then patches all class files in the jar.
"""
import zipfile, struct, shutil, sys, os, re
sys.stdout.reconfigure(encoding='utf-8')

JAR_PATH = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
YARN_TINY = os.path.expanduser(r'~\.gradle\caches\fabric-loom\1.17.1\net.fabricmc.yarn.1_17_1.1.17.1+build.38\mappings-base.tiny')
TSRG_FILE = r'tools\mcp-1171\extracted\config\joined.tsrg'
PROGUARD_FILE = r'build\mojang-client-1.17.1.txt'

def parse_yarn_tiny(path):
    """Parse Yarn v1 tiny file. Returns class map, method map, field map."""
    classes = {}  # obf -> yarn
    methods = {}  # (obf_class, obf_name, desc) -> yarn_name
    fields = {}   # (obf_class, obf_name) -> yarn_name
    with open(path, encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t')
            if not parts or parts[0] == 'v1': continue
            if parts[0] == 'CLASS' and len(parts) >= 4:
                classes[parts[1]] = parts[3]
            elif parts[0] == 'METHOD' and len(parts) >= 6:
                methods[(parts[1], parts[3], parts[2])] = parts[5]
            elif parts[0] == 'FIELD' and len(parts) >= 6:
                fields[(parts[1], parts[3])] = parts[5]
    return classes, methods, fields

def parse_tsrg(path):
    """Parse TSRG2 file. Returns class map, method map, field map."""
    classes = {}  # obf -> srg_intermediary
    methods = {}  # (obf_class, obf_name, desc) -> srg_name
    fields = {}   # (obf_class, obf_name) -> srg_name
    current_obf = None
    current_srg = None
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line.startswith('tsrg2'): continue
            if not line.startswith('\t'):
                parts = line.split()
                if len(parts) >= 2:
                    current_obf = parts[0]
                    current_srg = parts[1]
                    classes[current_obf] = current_srg
            elif current_obf:
                parts = line.strip().split()
                if len(parts) >= 3 and '(' in parts[1]:
                    # method: obf_name desc srg_name id
                    methods[(current_obf, parts[0], parts[1])] = parts[2]
                elif len(parts) >= 2 and '(' not in parts[1]:
                    # field: obf_name srg_name id
                    fields[(current_obf, parts[0])] = parts[1]
    return classes, methods, fields

def parse_proguard(path):
    """Parse Mojang ProGuard mapping. Returns obf -> mojang class map, and obf field/method maps."""
    classes = {}  # obf_internal -> mojang_internal
    methods = {}  # (obf_internal, obf_name) -> mojang_name
    fields = {}   # (obf_internal, obf_name) -> mojang_name
    current_obf = None
    current_mojang = None
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line.startswith('#'): continue
            if not line.startswith(' '):
                # class line: "package.Class -> obf:"
                arrow = line.find(' -> ')
                if arrow < 0: continue
                mojang_fqcn = line[:arrow].strip()
                obf_fqcn = line[arrow+4:].rstrip(':').strip()
                current_mojang = mojang_fqcn.replace('.', '/')
                current_obf = obf_fqcn.replace('.', '/')
                classes[current_obf] = current_mojang
            elif current_obf:
                # member line: "type name -> obf_name"
                arrow = line.find(' -> ')
                if arrow < 0: continue
                left = line[:arrow].strip()
                obf_name = line[arrow+4:].strip()
                parts = left.split()
                if len(parts) >= 2:
                    member_name = parts[-1]
                    if '(' in left:
                        # method
                        methods[(current_obf, obf_name)] = member_name
                    else:
                        # field
                        fields[(current_obf, obf_name)] = member_name
    return classes, methods, fields

def build_bare_name_replacements(yarn_classes, yarn_methods, yarn_fields,
                                  srg_classes, srg_methods, srg_fields,
                                  mojang_classes, mojang_methods, mojang_fields):
    """Build a mapping of bare Yarn names -> SRG names."""
    replacements = {}
    
    # Build obf -> mojang class lookup
    obf_to_mojang = {}
    for obf, mojang in mojang_classes.items():
        obf_to_mojang[obf] = mojang
    
    # Build obf -> yarn class lookup
    obf_to_yarn = {}
    for obf, yarn in yarn_classes.items():
        obf_to_yarn[obf] = yarn
    
    # Process classes (path-qualified, not bare)
    # Skip - these are handled by pathQualified in the Gradle build
    
    # Process methods: Yarn bare name -> SRG name
    # Only include methods where the Yarn name differs from SRG
    yarn_method_to_srg = {}  # yarn_name -> set of srg_names
    for (obf_class, obf_name, desc), yarn_name in yarn_methods.items():
        srg_name = srg_methods.get((obf_class, obf_name, desc))
        if srg_name and yarn_name != srg_name and not yarn_name.startswith('method_'):
            yarn_method_to_srg.setdefault(yarn_name, set()).add(srg_name)
    
    # Only add unambiguous mappings
    for yarn_name, srg_names in yarn_method_to_srg.items():
        if len(srg_names) == 1 and len(yarn_name) >= 5:
            replacements[yarn_name] = srg_names.pop()
    
    # Process fields: Yarn bare name -> SRG name
    yarn_field_to_srg = {}
    for (obf_class, obf_name), yarn_name in yarn_fields.items():
        srg_name = srg_fields.get((obf_class, obf_name))
        if srg_name and yarn_name != srg_name and not yarn_name.startswith('field_'):
            yarn_field_to_srg.setdefault(yarn_name, set()).add(srg_name)
    
    for yarn_name, srg_names in yarn_field_to_srg.items():
        if len(srg_names) == 1 and len(yarn_name) >= 5:
            replacements[yarn_name] = srg_names.pop()
    
    return replacements

def patch_class(data, replacements):
    """Patch bare names in a class file's constant pool and downgrade bytecode version."""
    if len(data) < 10:
        return data, 0
    
    data = bytearray(data)
    
    # Downgrade class file version to Java 17 (61.0)
    major = struct.unpack('>H', data[6:8])[0]
    if major > 61:
        data[6] = 0
        data[7] = 61
    
    pos = 8  # 4 magic + 2 minor + 2 major
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    
    utf8_entries = []  # (cp_index, length_field_pos, data_pos, length)
    i = 1
    scan_pos = pos
    while i < cp_count:
        tag = data[scan_pos]
        scan_pos += 1
        if tag == 1:  # UTF8
            length = struct.unpack('>H', data[scan_pos:scan_pos+2])[0]
            utf8_entries.append((i, scan_pos, scan_pos + 2, length))
            scan_pos += 2 + length
        elif tag in (3, 4): scan_pos += 4
        elif tag in (5, 6): scan_pos += 8; i += 1
        elif tag in (7, 8): scan_pos += 2
        elif tag in (9, 10, 11, 18): scan_pos += 4
        elif tag == 12: scan_pos += 4
        elif tag == 15: scan_pos += 3
        elif tag in (16, 19, 20): scan_pos += 2
        else: break
        i += 1
    
    patches = []
    for cp_idx, length_field_pos, data_pos, length in utf8_entries:
        s = data[data_pos:data_pos+length].decode('utf-8', errors='replace')
        if s in replacements:
            new_s = replacements[s]
            if new_s != s:
                patches.append((length_field_pos, data_pos, length, new_s))
    
    if not patches:
        return bytes(data), 0
    
    # Apply patches backwards to maintain offsets
    patches.sort(key=lambda x: x[1], reverse=True)
    for length_field_pos, data_pos, length, new_s in patches:
        new_bytes = new_s.encode('utf-8')
        new_len = len(new_bytes)
        data[length_field_pos] = (new_len >> 8) & 0xFF
        data[length_field_pos + 1] = new_len & 0xFF
        data[data_pos:data_pos+length] = new_bytes
    
    return bytes(data), len(patches)

def main():
    print("=== Yarn→SRG Bare Name Remapper ===")
    
    # Check files exist
    for path, name in [(JAR_PATH, "JAR"), (YARN_TINY, "Yarn tiny"), (TSRG_FILE, "TSRG"), (PROGUARD_FILE, "ProGuard")]:
        if not os.path.exists(path):
            print(f"ERROR: {name} not found: {path}")
            return
        print(f"  {name}: {path}")
    
    # Parse mappings
    print("\nParsing mappings...")
    yarn_classes, yarn_methods, yarn_fields = parse_yarn_tiny(YARN_TINY)
    print(f"  Yarn: {len(yarn_classes)} classes, {len(yarn_methods)} methods, {len(yarn_fields)} fields")
    
    srg_classes, srg_methods, srg_fields = parse_tsrg(TSRG_FILE)
    print(f"  SRG: {len(srg_classes)} classes, {len(srg_methods)} methods, {len(srg_fields)} fields")
    
    mojang_classes, mojang_methods, mojang_fields = parse_proguard(PROGUARD_FILE)
    print(f"  Mojang: {len(mojang_classes)} classes, {len(mojang_methods)} methods, {len(mojang_fields)} fields")
    
    # Build replacements
    print("\nBuilding replacement table...")
    replacements = build_bare_name_replacements(
        yarn_classes, yarn_methods, yarn_fields,
        srg_classes, srg_methods, srg_fields,
        mojang_classes, mojang_methods, mojang_fields
    )
    print(f"  Total bare name replacements: {len(replacements)}")
    
    # Also add intermediary->SRG mappings for method_NNN/field_NNN
    intermediary_pattern_replacements = {}
    for (obf_class, obf_name, desc), int_name in yarn_methods.items():
        if int_name.startswith('method_'):
            srg_name = srg_methods.get((obf_class, obf_name, desc))
            if srg_name and int_name != srg_name:
                intermediary_pattern_replacements[int_name] = srg_name
    for (obf_class, obf_name), int_name in yarn_fields.items():
        if int_name.startswith('field_'):
            srg_name = srg_fields.get((obf_class, obf_name))
            if srg_name and int_name != srg_name:
                intermediary_pattern_replacements[int_name] = srg_name
    
    all_replacements = {**replacements, **intermediary_pattern_replacements}
    print(f"  With intermediary mappings: {len(all_replacements)}")
    
    # Show some examples
    print("\nSample replacements:")
    for yarn, srg in sorted(all_replacements.items())[:20]:
        if not yarn.startswith(('method_', 'field_')):
            print(f"  {yarn} -> {srg}")
    
    # Backup jar
    backup = JAR_PATH + '.bak2'
    shutil.copy2(JAR_PATH, backup)
    print(f"\nBackup: {backup}")
    
    # Read all entries
    print("\nReading jar entries...")
    with zipfile.ZipFile(JAR_PATH, 'r') as zin:
        entries = {}
        for name in zin.namelist():
            entries[name] = zin.read(name)
    
    # Patch class files
    print("Patching class files...")
    patched = 0
    total_patches = 0
    for class_name, raw_data in entries.items():
        if not class_name.endswith('.class'):
            continue
        new_data, count = patch_class(raw_data, all_replacements)
        if count > 0:
            entries[class_name] = new_data
            patched += 1
            total_patches += count
    
    # Write new jar
    print(f"\nWriting patched jar...")
    with zipfile.ZipFile(JAR_PATH, 'w', zipfile.ZIP_DEFLATED) as zout:
        for name, data in entries.items():
            zout.writestr(name, data)
    
    print(f"\n=== Done ===")
    print(f"Patched {patched} classes, {total_patches} total name replacements")

if __name__ == '__main__':
    main()
