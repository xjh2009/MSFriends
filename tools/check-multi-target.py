import zipfile, struct, re

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

# Parse the pre-build SRG mapping tables from the build log
# Let's instead directly check the SRG file
srg_path = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.20.1-20230612.114412\client\data\mappings\joined.tsrg'

# Parse SRG to get field names per class
srg_fields = {}  # obf_class -> [(obf_field, srg_name)]
srg_methods = {}  # obf_class -> [(obf_method, obf_desc, srg_name)]
current_obf = None
with open(srg_path) as f:
    for line in f:
        if line.startswith('tsrg2'): continue
        if line.startswith('\t'):
            parts = line.strip().split()
            if not parts: continue
            if current_obf is None: continue
            if parts[0].startswith('(') and len(parts) >= 3:
                srg_methods.setdefault(current_obf, []).append((parts[0], parts[1] if len(parts) > 2 else '', parts[-1]))
            elif len(parts) >= 2:
                # Check if it's a field or method
                # For methods: obf_name (obf_desc) srg_name  -> parts[0] doesn't start with (
                # Actually in tsrg2, it could be: obf_name srg_name (2 parts for fields)
                # or: obf_name (desc) srg_name (3 parts for methods)
                if len(parts) == 2:
                    srg_fields.setdefault(current_obf, []).append((parts[0], parts[1]))
                elif len(parts) >= 3:
                    if parts[1].startswith('('):
                        srg_methods.setdefault(current_obf, []).append((parts[1], parts[2] if len(parts) > 2 else '', parts[0]))
                    else:
                        srg_fields.setdefault(current_obf, []).append((parts[0], parts[1]))
        else:
            parts = line.split()
            if len(parts) >= 2:
                current_obf = parts[0]

# Parse Yarn to get Yarn name → obf mapping
yarn_path = r'C:\Users\xjh37\.gradle\caches\fabric-loom\1.20.1\net.fabricmc.yarn.1_20_1.1.20.1+build.10\mappings-base.tiny'
yarn_fields = {}  # obf_class|obf_field -> (intermediary, yarn)
yarn_classes = {}  # obf_class -> (intermediary, yarn)
with open(yarn_path) as f:
    for line in f:
        parts = line.strip().split('\t')
        if not parts: continue
        if len(parts) >= 4 and parts[0] == 'CLASS':
            yarn_classes[parts[1]] = (parts[2], parts[3])
        elif len(parts) >= 6 and parts[0] == 'FIELD':
            yarn_fields[f'{parts[1]}|{parts[3]}'] = (parts[4], parts[5])

# Build Yarn bare name -> multiple SRG names mapping
yarn_to_srg_multi = {}  # yarn_name -> set of srg_names
for key, (int_name, yarn_name) in yarn_fields.items():
    obf_class, obf_field = key.split('|', 1)
    for (sf_obf, sf_srg) in srg_fields.get(obf_class, []):
        if sf_obf == obf_field:
            yarn_to_srg_multi.setdefault(yarn_name, set()).add(sf_srg)
            break

# Find multi-target bare names
multi_target = {name: srg_names for name, srg_names in yarn_to_srg_multi.items() if len(srg_names) > 1}

print(f'Total multi-target bare field names: {len(multi_target)}')
for name, srg_names in sorted(multi_target.items()):
    print(f'  "{name}" -> {srg_names}')

# Now check which mixin classes reference these multi-target names
print('\n--- Checking mixin classes for multi-target bare names ---')
with zipfile.ZipFile(jar_path) as z:
    mixin_classes = [n for n in z.namelist() if 'mixin' in n.lower() and n.endswith('.class') and '$' not in n]
    
    for cls_name in mixin_classes:
        data = z.read(cls_name)
        # Quick search for multi-target names
        for name in multi_target:
            if name.encode('utf-8') in data:
                print(f'  {cls_name} references "{name}"')
