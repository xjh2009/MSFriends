"""Convert Fabric refmap to Forge SRG format for 1.17.1"""
import json, zipfile, re, sys, os
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'build\versions-1.17.1-forge\libs\versions-1.17.1-forge-0.1.0+26.1.2-all.jar'
YARN_TINY = os.path.expanduser(r'~\.gradle\caches\fabric-loom\1.17.1\net.fabricmc.yarn.1_17_1.1.17.1+build.38\mappings-base.tiny')
TSRG = r'tools\mcp-1171\extracted\config\joined.tsrg'
PROGUARD = r'build\mojang-client-1.17.1.txt'

with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}
refmap_name = [n for n in entries if n.endswith('-refmap.json')][0]
refmap = json.loads(entries[refmap_name].decode('utf-8'))

# Parse Yarn tiny
int_class_to_obf = {}
int_method_to_obf = {}
int_field_to_obf = {}
with open(YARN_TINY, encoding='utf-8') as f:
    for line in f:
        p = line.rstrip().split('\t')
        if p[0] == 'CLASS' and len(p) >= 4:
            int_class_to_obf[p[2]] = p[1]
        elif p[0] == 'METHOD' and len(p) >= 6:
            int_method_to_obf[p[4]] = (p[1], p[3], p[2])
        elif p[0] == 'FIELD' and len(p) >= 6:
            int_field_to_obf[p[4]] = (p[1], p[3])

# Parse TSRG
srg_method = {}
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
            if len(p) >= 3 and '(' in p[1]:
                srg_method[(cur_obf, p[0], p[1])] = p[2]
            elif len(p) >= 2:
                srg_field[(cur_obf, p[0])] = p[1]

# Parse ProGuard
mojang_class = {}
with open(PROGUARD, encoding='utf-8') as f:
    for line in f:
        line = line.rstrip()
        if line.startswith('#') or line.startswith(' '): continue
        arrow = line.find(' -> ')
        if arrow < 0: continue
        mojang_class[line[arrow+4:].rstrip(':').replace('.', '/')] = line[:arrow].replace('.', '/')

# Build full path intermediary -> Mojang
int_to_mojang = {}
for int_name, obf_name in int_class_to_obf.items():
    m = mojang_class.get(obf_name)
    if m: int_to_mojang[int_name] = m

int_to_srg_method = {}
for int_name, (obf_cls, obf_name, obf_desc) in int_method_to_obf.items():
    srg = srg_method.get((obf_cls, obf_name, obf_desc))
    if srg: int_to_srg_method[int_name] = srg

int_to_srg_field = {}
for int_name, (obf_cls, obf_name) in int_field_to_obf.items():
    srg = srg_field.get((obf_cls, obf_name))
    if srg: int_to_srg_field[int_name] = srg

print("Maps: class=%d, method=%d, field=%d" % (len(int_to_mojang), len(int_to_srg_method), len(int_to_srg_field)))
print("  net/minecraft/class_442 -> %s" % int_to_mojang.get("net/minecraft/class_442", "NOT FOUND"))

def convert_value(val):
    # Replace full intermediary class paths with Mojang paths
    # Sort by length descending to avoid partial matches
    for int_path in sorted(int_to_mojang.keys(), key=len, reverse=True):
        if int_path in val:
            val = val.replace(int_path, int_to_mojang[int_path])
    for int_name in sorted(int_to_srg_method.keys(), key=len, reverse=True):
        if int_name in val:
            val = val.replace(int_name, int_to_srg_method[int_name])
    for int_name in sorted(int_to_srg_field.keys(), key=len, reverse=True):
        if int_name in val:
            val = val.replace(int_name, int_to_srg_field[int_name])
    return val

count = 0
for cls, anns in refmap.get('mappings', {}).items():
    for ann, targets in anns.items():
        if isinstance(targets, str):
            nv = convert_value(targets)
            if nv != targets: anns[ann] = nv; count += 1
        elif isinstance(targets, dict):
            for k, v in targets.items():
                if isinstance(v, str):
                    nv = convert_value(v)
                    if nv != v: targets[k] = nv; count += 1

print("Converted %d entries" % count)
for cls, anns in list(refmap['mappings'].items())[:5]:
    for ann, val in list(anns.items())[:1]:
        if isinstance(val, str):
            print("  %s.%s -> %s" % (cls, ann, val))

entries[refmap_name] = json.dumps(refmap, indent=2, ensure_ascii=False).encode('utf-8')
with zipfile.ZipFile(JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, data in entries.items():
        zout.writestr(name, data)
print("Done!")
