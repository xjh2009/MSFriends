import re

# Parse SRG mapping for connection fields
srg_path = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.20.1-20230612.114412\client\data\mappings\joined.tsrg'
with open(srg_path) as f:
    content = f.read()

# Find obf classes for aiw, aja, aix (ClientHandshakePacketListenerImpl, ServerLoginPacketListenerImpl, etc.)
# Parse all class mappings
current_obf = None
current_srg = None
entries = []
for line in content.split('\n'):
    if line.startswith('tsrg2'): continue
    if line.startswith('\t'):
        parts = line.strip().split()
        if len(parts) >= 2 and current_obf:
            if parts[0].startswith('('):
                # method
                pass
            else:
                # field: obf_name srg_name
                entries.append((current_obf, current_srg, parts[0], parts[1]))
    else:
        parts = line.split()
        if len(parts) >= 2:
            current_obf = parts[0]
            current_srg = parts[1]

# Show entries for the relevant obf classes
for obf, srg, field_obf, field_srg in entries:
    if obf in ('aiw', 'aja', 'aix', 'ajc', 'sd'):
        print(f'{obf} -> {srg}: field {field_obf} -> {field_srg}')

# Also search for 'render' method on eus
print('\n--- Methods on eus (SimpleOptionsSubScreen) ---')
current_obf = None
for line in content.split('\n'):
    if line.startswith('tsrg2'): continue
    if line.startswith('\t'):
        parts = line.strip().split()
        if len(parts) >= 3 and current_obf == 'eus':
            if parts[0].startswith('('):
                print(f'  method {parts[0]} {parts[1]} -> {parts[2]}')
    else:
        parts = line.split()
        if len(parts) >= 2:
            current_obf = parts[0]
