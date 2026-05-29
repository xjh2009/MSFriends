"""
Smart Yarn→SRG remapper that resolves ambiguous bare names using class hierarchy.
For each class file, reads super_class and resolves inherited field/method names.
"""
import zipfile, struct, shutil, sys, os, re
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
YARN_TINY = os.path.expanduser(r'~\.gradle\caches\fabric-loom\1.17.1\net.fabricmc.yarn.1_17_1.1.17.1+build.38\mappings-base.tiny')
TSRG = r'tools\mcp-1171\extracted\config\joined.tsrg'

# Parse Yarn tiny
yarn_class_obf_to_int = {}  # obf -> intermediary
yarn_class_obf_to_yarn = {}  # obf -> yarn
yarn_methods = {}  # (obf_class, desc, obf_name) -> (intermediary, yarn)
yarn_fields = {}   # (obf_class, obf_name) -> (intermediary, yarn)

with open(YARN_TINY, encoding='utf-8') as f:
    for line in f:
        p = line.rstrip().split('\t')
        if p[0] == 'CLASS' and len(p) >= 4:
            yarn_class_obf_to_int[p[1]] = p[2]
            yarn_class_obf_to_yarn[p[1]] = p[3]
        elif p[0] == 'METHOD' and len(p) >= 6:
            yarn_methods[(p[1], p[2], p[3])] = (p[4], p[5])
        elif p[0] == 'FIELD' and len(p) >= 6:
            yarn_fields[(p[1], p[3])] = (p[4], p[5])

# Parse TSRG
srg_class = {}  # obf -> srg
srg_method = {}  # (obf_class, obf_name, desc) -> srg_name
srg_field = {}   # (obf_class, obf_name) -> srg_name
cur_obf = None
with open(TSRG, encoding='utf-8') as f:
    for line in f:
        line = line.rstrip()
        if line.startswith('tsrg2'): continue
        if not line.startswith('\t'):
            p = line.split()
            if len(p) >= 2:
                cur_obf = p[0]
                srg_class[cur_obf] = p[1]
        elif cur_obf:
            p = line.strip().split()
            if len(p) >= 3 and '(' in p[1]:
                srg_method[(cur_obf, p[0], p[1])] = p[2]
            elif len(p) >= 2:
                srg_field[(cur_obf, p[0])] = p[1]

# Build intermediary -> obf class lookup
int_class_to_obf = {}
for obf, int_name in yarn_class_obf_to_int.items():
    int_class_to_obf[int_name] = obf

# Build yarn class path -> obf
yarn_path_to_obf = {}
for obf, yarn_path in yarn_class_obf_to_yarn.items():
    yarn_path_to_obf[yarn_path] = obf

# Build SRG class path -> obf
srg_path_to_obf = {}
for obf, srg_path in srg_class.items():
    srg_path_to_obf[srg_path] = obf

def get_obf_class_from_cp(class_bytes, class_cp_index):
    """Get obf class name from a Class constant pool entry."""
    pos = 8
    cp_count = struct.unpack('>H', class_bytes[pos:pos+2])[0]
    pos += 2
    i = 1
    sp = pos
    while i < cp_count:
        tag = class_bytes[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', class_bytes[sp:sp+2])[0]; sp += 2 + l
        elif tag in (3,4): sp += 4
        elif tag in (5,6): sp += 8; i += 1
        elif tag == 7:
            if i == class_cp_index:
                name_idx = struct.unpack('>H', class_bytes[sp:sp+2])[0]
                # Now find the UTF8 at name_idx
                sp2 = pos
                j = 1
                while j < cp_count:
                    tag2 = class_bytes[sp2]; sp2 += 1
                    if tag2 == 1:
                        l2 = struct.unpack('>H', class_bytes[sp2:sp2+2])[0]
                        if j == name_idx:
                            return class_bytes[sp2+2:sp2+2+l2].decode('utf-8', errors='replace')
                        sp2 += 2 + l2
                    elif tag2 in (3,4): sp2 += 4
                    elif tag2 in (5,6): sp2 += 8; j += 1
                    elif tag2 in (7,8): sp2 += 2
                    elif tag2 in (9,10,11,18): sp2 += 4
                    elif tag2 == 12: sp2 += 4
                    elif tag2 == 15: sp2 += 3
                    elif tag2 in (16,19,20): sp2 += 2
                    else: break
                    j += 1
                return None
            sp += 2
        elif tag == 8: sp += 2
        elif tag in (9,10,11,18): sp += 4
        elif tag == 12: sp += 4
        elif tag == 15: sp += 3
        elif tag in (16,19,20): sp += 2
        else: break
        i += 1
    return None

def find_srg_for_yarn_name(yarn_name, parent_obf_classes, is_field):
    """Find SRG name for a Yarn field/method name by checking parent classes."""
    for parent_obf in parent_obf_classes:
        if is_field:
            for (obf_cls, obf_name), (int_name, yn) in yarn_fields.items():
                if obf_cls == parent_obf and yn == yarn_name:
                    srg = srg_field.get((obf_cls, obf_name))
                    if srg:
                        return srg
        else:
            for (obf_cls, desc, obf_name), (int_name, yn) in yarn_methods.items():
                if obf_cls == parent_obf and yn == yarn_name:
                    srg = srg_method.get((obf_cls, obf_name, desc))
                    if srg:
                        return srg
    return None

def get_parent_classes(class_bytes, max_depth=5):
    """Get list of obf parent classes (up to max_depth)."""
    parents = []
    current = class_bytes
    for _ in range(max_depth):
        # Get this_class
        pos = 8
        this_class_idx = struct.unpack('>H', current[pos+4:pos+6])[0]
        super_class_idx = struct.unpack('>H', current[pos+6:pos+8])[0]
        if super_class_idx == 0:
            break
        # Get super class name from constant pool
        super_name = get_obf_class_from_cp(current, super_class_idx)
        if not super_name:
            break
        # super_name is the internal name (e.g., net/minecraft/client/gui/screens/Screen)
        # Convert to obf
        obf = srg_path_to_obf.get(super_name) or yarn_path_to_obf.get(super_name)
        if not obf:
            # Try intermediary
            obf = int_class_to_obf.get(super_name)
        if obf:
            parents.append(obf)
        else:
            break
        # TODO: load parent class file for deeper hierarchy
        break
    return parents

# Scan all class files
with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}

shutil.copy2(JAR, JAR + '.bak4')

# Find all Yarn bare names (unambiguous and ambiguous)
all_yarn_names = {}
for (obf_cls, obf_name), (int_name, yarn_name) in yarn_fields.items():
    srg = srg_field.get((obf_cls, obf_name))
    if srg and yarn_name != srg and len(yarn_name) >= 2:
        all_yarn_names.setdefault(yarn_name, set()).add(srg)
for (obf_cls, desc, obf_name), (int_name, yarn_name) in yarn_methods.items():
    srg = srg_method.get((obf_cls, obf_name, desc))
    if srg and yarn_name != srg and len(yarn_name) >= 2:
        all_yarn_names.setdefault(yarn_name, set()).add(srg)

# Unambiguous names
unambiguous = {n: names.pop() for n, names in all_yarn_names.items() if len(names) == 1}
# Ambiguous names
ambiguous = {n: names for n, names in all_yarn_names.items() if len(names) > 1}

print("Unambiguous: %d, Ambiguous: %d" % (len(unambiguous), len(ambiguous)))

total_patches = 0
patched_classes = 0

for cls_name, raw_data in entries.items():
    if not cls_name.endswith('.class') or 'dev/msf/friends/' not in cls_name:
        continue
    
    data = bytearray(raw_data)
    pos = 8
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    
    # Parse constant pool
    utf8_entries = []
    class_refs = {}  # cp_idx -> name_utf8_idx
    nat_refs = {}    # cp_idx -> (name_idx, desc_idx)
    mf_refs = {}     # cp_idx -> (class_idx, nat_idx)
    
    i = 1; sp = pos
    while i < cp_count:
        tag = data[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', data[sp:sp+2])[0]
            utf8_entries.append((i, sp, sp+2, l))
            sp += 2 + l
        elif tag in (3,4): sp += 4
        elif tag in (5,6): sp += 8; i += 1
        elif tag == 7:
            class_refs[i] = struct.unpack('>H', data[sp:sp+2])[0]
            sp += 2
        elif tag == 8: sp += 2
        elif tag in (9,10,11):
            c = struct.unpack('>H', data[sp:sp+2])[0]
            n = struct.unpack('>H', data[sp+2:sp+4])[0]
            mf_refs[i] = (c, n)
            sp += 4
        elif tag == 12:
            n = struct.unpack('>H', data[sp:sp+2])[0]
            d = struct.unpack('>H', data[sp+2:sp+4])[0]
            nat_refs[i] = (n, d)
            sp += 4
        elif tag == 15: sp += 3
        elif tag in (16,19,20): sp += 2
        elif tag == 18: sp += 4
        else: break
        i += 1
    
    # Build utf8_idx -> (list_idx, value) map
    utf8_map = {}
    for list_idx, (cp_idx, lfp, dp, length) in enumerate(utf8_entries):
        s = data[dp:dp+length].decode('utf-8', errors='replace')
        utf8_map[cp_idx] = (list_idx, s, lfp, dp, length)
    
    # Get parent classes for ambiguous name resolution
    parent_obf_classes = get_parent_classes(raw_data)
    
    # Find patches
    patches = []
    for list_idx, (cp_idx, lfp, dp, length) in enumerate(utf8_entries):
        s = utf8_map[cp_idx][1]
        
        # Try unambiguous first
        if s in unambiguous:
            patches.append((lfp, dp, length, unambiguous[s]))
            continue
        
        # Try ambiguous with class hierarchy resolution
        if s in ambiguous and parent_obf_classes:
            # Check if this UTF8 is referenced by a Fieldref/Methodref on the current class
            # or a parent class
            for nat_idx, (name_idx, desc_idx) in nat_refs.items():
                if name_idx != cp_idx:
                    continue
                for mf_idx, (class_idx, cnat_idx) in mf_refs.items():
                    if cnat_idx != nat_idx:
                        continue
                    # Get the class this field/method belongs to
                    class_name_idx = class_refs.get(class_idx)
                    if not class_name_idx:
                        continue
                    class_name = utf8_map.get(class_name_idx)
                    if not class_name:
                        continue
                    class_str = class_name[1]
                    
                    # Check if it's the current class or a parent
                    is_current = class_str == cls_name.replace('.class', '').replace('.', '/')
                    is_parent = False
                    for p in parent_obf_classes:
                        p_yarn = yarn_class_obf_to_yarn.get(p)
                        p_srg = srg_class.get(p)
                        if class_str == p_yarn or class_str == p_srg:
                            is_parent = True
                            break
                    
                    if is_current or is_parent:
                        # Determine if this is a field or method
                        desc_utf8 = utf8_map.get(desc_idx)
                        if desc_utf8:
                            desc_str = desc_utf8[1]
                            is_field = not desc_str.startswith('(')
                            srg = find_srg_for_yarn_name(s, parent_obf_classes, is_field)
                            if srg:
                                patches.append((lfp, dp, length, srg))
                                break
                else:
                    continue
                break
    
    if not patches:
        continue
    
    # Apply patches backwards
    patches.sort(key=lambda x: x[2], reverse=True)
    for lfp, dp, length, new_s in patches:
        nb = new_s.encode('utf-8')
        data[lfp] = (len(nb) >> 8) & 0xFF
        data[lfp+1] = len(nb) & 0xFF
        data[dp:dp+length] = nb
    
    entries[cls_name] = bytes(data)
    total_patches += len(patches)
    patched_classes += 1
    if len(patches) > 0:
        print("  %s: %d patches" % (cls_name, len(patches)))

# Write jar
with zipfile.ZipFile(JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, d in entries.items():
        zout.writestr(name, d)

print("\nPatched %d classes, %d total patches" % (patched_classes, total_patches))
