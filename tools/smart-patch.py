"""
Smart patch: for each class, find its parent chain and resolve inherited fields.
Only patches .class files, preserves refmap and other resources.
"""
import zipfile, struct, sys, shutil
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
YARN_TINY = r'C:\Users\xjh37\.gradle\caches\fabric-loom\1.17.1\net.fabricmc.yarn.1_17_1.1.17.1+build.38\mappings-base.tiny'
TSRG = r'tools\mcp-1171\extracted\config\joined.tsrg'

# Parse Yarn tiny: class_name -> {yarn_field -> srg_field}
yarn_fields_by_class = {}  # yarn_class -> {yarn_name -> srg_name}
with open(YARN_TINY, encoding='utf-8') as f:
    for line in f:
        p = line.rstrip().split('\t')
        if p[0] == 'FIELD' and len(p) >= 6:
            obf_cls = p[1]
            srg_int = p[4]
            yarn_name = p[5]
            yarn_fields_by_class.setdefault(obf_cls, {})[yarn_name] = srg_int

# Parse TSRG
srg_field = {}
cur_obf = None
with open(TSRG, encoding='utf-8') as f:
    for line in f:
        line = line.rstrip()
        if line.startswith('tsrg2'): continue
        if not line.startswith('\t'):
            p = line.split()
            if len(p) >= 2: cur_obf = p[0]
        elif cur_obf:
            p = line.strip().split()
            if len(p) >= 2 and '(' not in p[1]:
                srg_field[(cur_obf, p[0])] = p[1]

# Build yarn_class -> {yarn_name -> srg_name} using TSRG
class_fields = {}  # obf_class -> {yarn_name -> srg_name}
for obf_cls, fields in yarn_fields_by_class.items():
    for yarn_name, srg_int in fields.items():
        srg = srg_field.get((obf_cls, srg_int))
        if srg:
            class_fields.setdefault(obf_cls, {})[yarn_name] = srg

# Known safe bare name patches (confirmed correct SRG)
SAFE = {
    'getMessage': 'm_6035_',
    'getWindowTitle': 'm_91270_',
    'getInstance': 'm_91087_',
    'setScreen': 'm_91152_',
    'getServer': 'm_91092_',
    'getSessionService': 'm_91108_',
    'EMPTY': 'f_131282_',
    'setShaderColor': 'm_157429_',
    'setShaderTexture': 'm_157456_',
    'disableBlend': 'm_69461_',
    'enableBlend': 'm_69478_',
    'addDrawableChild': 'm_142416_',
    'drawTexture': 'm_93208_',
    'getString': 'm_50789_',
    'isDown': 'm_90864_',
    'setDown': 'm_90837_',
    'consumeClick': 'm_90863_',
    'getKey': 'm_90868_',
}

def parse_class(data):
    """Parse constant pool and return utf8_entries, classRefs, super_name, this_name"""
    pos = 8
    cc = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    utf8s = {}
    classRefs = {}
    i = 1; sp = pos
    while i < cc:
        tag = data[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', data[sp:sp+2])[0]; sp += 2
            utf8s[i] = data[sp:sp+l].decode('utf-8', errors='replace'); sp += l
        elif tag in (3,4): sp += 4
        elif tag in (5,6): sp += 8; i += 1
        elif tag == 7:
            classRefs[i] = struct.unpack('>H', data[sp:sp+2])[0]
            sp += 2
        elif tag == 8: sp += 2
        elif tag in (9,10,11,18): sp += 4
        elif tag == 12: sp += 4
        elif tag == 15: sp += 3
        elif tag in (16,19,20): sp += 2
        else: break
        i += 1
    # sp now points to access_flags
    access = struct.unpack('>H', data[sp:sp+2])[0]; sp += 2
    this_idx = struct.unpack('>H', data[sp:sp+2])[0]; sp += 2
    super_idx = struct.unpack('>H', data[sp:pos+2])[0] if pos+2 <= len(data) else 0
    super_idx = struct.unpack('>H', data[sp:sp+2])[0]; sp += 2
    this_name = utf8s.get(classRefs.get(this_idx), '?')
    super_name = utf8s.get(classRefs.get(super_idx), '?')
    return utf8s, classRefs, this_name, super_name

# Map Mojang class names to obf class names
mojang_to_obf = {}
obf_to_mojang = {}
with open('build/mojang-client-1.17.1.txt', encoding='utf-8') as f:
    for line in f:
        line = line.rstrip()
        if line.startswith('#') or line.startswith(' '): continue
        arrow = line.find(' -> ')
        if arrow < 0: continue
        mojang = line[:arrow].replace('.', '/')
        obf = line[arrow+4:].rstrip(':').replace('.', '/')
        mojang_to_obf[mojang] = obf
        obf_to_mojang[obf] = mojang

def get_parent_chain(class_name, max_depth=5):
    """Get obf class names in parent chain"""
    chain = []
    current = class_name
    for _ in range(max_depth):
        obf = mojang_to_obf.get(current)
        if obf:
            chain.append(obf)
            # Find parent via obf class name
            # Need to check the Yarn parent
            parent_mojang = None
            # Look up parent in class_fields (which is keyed by obf)
            # Actually we need the parent from the class file itself
            break
        break
    return chain

def get_srg_for_bare_name(bare_name, class_name):
    """Find SRG name for a bare Yarn name by checking class hierarchy"""
    # First check SAFE map
    if bare_name in SAFE:
        return SAFE[bare_name]
    
    # Check if this class has the field
    obf = mojang_to_obf.get(class_name)
    if obf and obf in class_fields:
        srg = class_fields[obf].get(bare_name)
        if srg:
            return srg
    
    return None

# Read all entries
with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}

# Build class hierarchy from class files
class_hierarchy = {}  # class_name -> parent_class_name
for name, data in entries.items():
    if not name.endswith('.class'): continue
    try:
        utf8s, classRefs, this_name, super_name = parse_class(data)
        class_hierarchy[this_name] = super_name
    except:
        pass

def get_ancestor_fields(class_name, depth=5):
    """Get all inherited field mappings by walking up the class hierarchy"""
    fields = {}
    current = class_name
    for _ in range(depth):
        obf = mojang_to_obf.get(current)
        if obf and obf in class_fields:
            fields.update(class_fields[obf])
        parent = class_hierarchy.get(current)
        if not parent or parent == 'java/lang/Object':
            break
        current = parent
    return fields

shutil.copy2(JAR, JAR + '.bak8')

total = 0
patched_classes = 0

for cls_name, raw_data in entries.items():
    if not cls_name.endswith('.class') or 'dev/msf/friends/' not in cls_name:
        continue
    
    data = bytearray(raw_data)
    pos = 8; cc = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    utf8_entries = []
    classRefs = {}
    i = 1; sp = pos
    while i < cc:
        tag = data[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', data[sp:sp+2])[0]
            utf8_entries.append((i, sp, sp+2, l))
            sp += 2 + l
        elif tag in (3,4): sp += 4
        elif tag in (5,6): sp += 8; i += 1
        elif tag == 7:
            classRefs[i] = struct.unpack('>H', data[sp:sp+2])[0]
            sp += 2
        elif tag == 8: sp += 2
        elif tag in (9,10,11,18): sp += 4
        elif tag == 12: sp += 4
        elif tag == 15: sp += 3
        elif tag in (16,19,20): sp += 2
        else: break
        i += 1
    
    # Get this class name from constant pool
    utf8s = {}
    classRefs2 = {}
    i2 = 1; sp2 = pos
    while i2 < cc:
        tag2 = data[sp2]; sp2 += 1
        if tag2 == 1:
            l2 = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
            utf8s[i2] = data[sp2:sp2+l2].decode('utf-8', errors='replace'); sp2 += l2
        elif tag2 in (3,4): sp2 += 4
        elif tag2 in (5,6): sp2 += 8; i2 += 1
        elif tag2 == 7:
            classRefs2[i2] = struct.unpack('>H', data[sp2:sp2+2])[0]
            sp2 += 2
        elif tag2 == 8: sp2 += 2
        elif tag2 in (9,10,11,18): sp2 += 4
        elif tag2 == 12: sp2 += 4
        elif tag2 == 15: sp2 += 3
        elif tag2 in (16,19,20): sp2 += 2
        else: break
        i2 += 1
    access = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
    this_idx = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
    this_name_idx = classRefs2.get(this_idx)
    this_name = utf8s.get(this_name_idx, '?') if this_name_idx else '?'
    
    # Get ancestor fields
    ancestor_fields = get_ancestor_fields(this_name)
    
    patches = []
    for cp_idx, lfp, dp, length in utf8_entries:
        s = data[dp:dp+length].decode('utf-8', errors='replace')
        # Check SAFE map first
        if s in SAFE:
            patches.append((lfp, dp, length, SAFE[s]))
        # Check ancestor fields
        elif s in ancestor_fields:
            patches.append((lfp, dp, length, ancestor_fields[s]))
    
    if not patches:
        continue
    
    patches.sort(key=lambda x: x[1], reverse=True)
    for lfp, dp, length, new_s in patches:
        nb = new_s.encode('utf-8')
        data[lfp] = (len(nb) >> 8) & 0xFF
        data[lfp+1] = len(nb) & 0xFF
        data[dp:dp+length] = nb
    
    entries[cls_name] = bytes(data)
    total += len(patches)
    patched_classes += 1
    print('  %s: %d patches' % (cls_name.split('/')[-1], len(patches)))

with zipfile.ZipFile(JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, d in entries.items():
        zout.writestr(name, d)

print('\nPatched %d classes, %d total' % (patched_classes, total))
