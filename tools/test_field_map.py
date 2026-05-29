"""Quick test: build named->srg field mapping"""
import os, sys
home = os.path.expanduser('~')
yarn_path = os.path.join(home, '.gradle', 'caches', 'fabric-loom', '1.15.2',
    'net.fabricmc.yarn.1_15_2.1.15.2+build.17', 'mappings-base.tiny')
srg_path = os.path.join(home, '.gradle', 'caches', 'minecraftforge', 'forgegradle',
    'mavenizer', 'caches', 'mcp', 'de', 'oceanlabs', 'mcp', 'mcp_config',
    '1.15.2-20200515.085601', 'client', 'data', 'mappings', 'joined.tsrg')

off_fld_named = {}
with open(yarn_path, 'r', encoding='utf-8') as f:
    for line in f:
        parts = line.rstrip('\n').split('\t')
        if parts[0] == 'FIELD' and len(parts) >= 6:
            off_fld_named[(parts[1], parts[3])] = parts[5]

off_fld_srg = {}
cur_off = ''
with open(srg_path, 'r', encoding='utf-8') as f:
    for line in f:
        s = line.rstrip('\n')
        if s.startswith('tsrg2') or not s: continue
        if s.startswith('\t'):
            parts = s.strip().split()
            if len(parts) >= 2 and '(' not in parts[0]:
                off_fld_srg[(cur_off, parts[0])] = parts[1]
        else:
            parts = s.split()
            if len(parts) >= 2:
                cur_off = parts[0]

named_fld_to_srg = {}
for (off_cls, off_fld), named_name in off_fld_named.items():
    srg_name = off_fld_srg.get((off_cls, off_fld))
    if srg_name and named_name != srg_name:
        named_fld_to_srg[named_name] = srg_name

print(f'Named->SRG field mappings: {len(named_fld_to_srg)}')
print(f'keysAll -> {named_fld_to_srg.get("keysAll", "MISSING")}')
print(f'keyForward -> {named_fld_to_srg.get("keyForward", "MISSING")}')
print(f'client -> {named_fld_to_srg.get("client", "MISSING")}')
