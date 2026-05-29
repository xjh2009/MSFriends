import zipfile, struct, re, sys

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

def read_u2(data, pos):
    return (data[pos] << 8) | data[pos+1]

def analyze_class(class_bytes):
    pos = 0
    magic = struct.unpack('>I', class_bytes[0:4])[0]
    if magic != 0xCAFEBABE:
        return None
    pos = 10
    cp_count = read_u2(class_bytes, pos)
    pos += 2
    
    utf8_entries = {}  # cp_idx -> value
    class_refs = {}  # cp_idx -> utf8_idx
    name_and_type = {}  # cp_idx -> (name_utf8_idx, desc_utf8_idx)
    field_method_refs = {}  # cp_idx -> (class_cp_idx, nat_cp_idx)
    
    i = 1
    while i < cp_count and pos < len(class_bytes):
        tag = class_bytes[pos]
        pos += 1
        if tag == 1:
            length = read_u2(class_bytes, pos)
            pos += 2
            value = class_bytes[pos:pos+length].decode('utf-8', errors='replace')
            utf8_entries[i] = value
            pos += length
        elif tag in (3, 4): pos += 4
        elif tag in (5, 6): pos += 8; i += 1
        elif tag == 7:
            name_idx = read_u2(class_bytes, pos)
            class_refs[i] = name_idx
            pos += 2
        elif tag == 8: pos += 2
        elif tag in (9, 10, 11):
            class_idx = read_u2(class_bytes, pos)
            nat_idx = read_u2(class_bytes, pos+2)
            field_method_refs[i] = (class_idx, nat_idx)
            pos += 4
        elif tag == 12:
            name_idx = read_u2(class_bytes, pos)
            desc_idx = read_u2(class_bytes, pos+2)
            name_and_type[i] = (name_idx, desc_idx)
            pos += 4
        elif tag == 15: pos += 3
        elif tag == 16: pos += 2
        elif tag in (17, 18): pos += 4
        elif tag in (19, 20): pos += 2
        else: break
        i += 1
    
    return utf8_entries, class_refs, name_and_type, field_method_refs

with zipfile.ZipFile(jar_path) as z:
    for target_class in ['dev/msf/friends/mixin/ClientLoginMixin.class',
                         'dev/msf/friends/mixin/ServerLoginMixin.class',
                         'dev/msf/friends/mixin/OnlineOptionsScreenMixin.class']:
        print(f"\n=== {target_class} ===")
        data = z.read(target_class)
        result = analyze_class(data)
        if result:
            utf8_entries, class_refs, name_and_type, field_method_refs = result
            
            # Find 'connection' entries
            connection_entries = [(idx, val) for idx, val in utf8_entries.items() if val == 'connection']
            print(f'Found {len(connection_entries)} "connection" UTF8 entries')
            
            for cp_idx, val in connection_entries:
                # Find NameAndType refs that reference this UTF8
                for nat_idx, (name_idx, desc_idx) in name_and_type.items():
                    if name_idx == cp_idx:
                        desc_val = utf8_entries.get(desc_idx, '???')
                        print(f'  NAT#{nat_idx}: name="{val}", desc="{desc_val}"')
                        
                        # Find Fieldrefs referencing this NAT
                        for fr_idx, (fr_class, fr_nat) in field_method_refs.items():
                            if fr_nat == nat_idx:
                                class_name_idx = class_refs.get(fr_class, 0)
                                class_name = utf8_entries.get(class_name_idx, '???')
                                print(f'    -> Fieldref#{fr_idx}: owner_class="{class_name}"')
            
            # Find 'render' entries
            render_entries = [(idx, val) for idx, val in utf8_entries.items() if val == 'render']
            print(f'Found {len(render_entries)} "render" UTF8 entries')
            
            for cp_idx, val in render_entries:
                for nat_idx, (name_idx, desc_idx) in name_and_type.items():
                    if name_idx == cp_idx:
                        desc_val = utf8_entries.get(desc_idx, '???')
                        print(f'  NAT#{nat_idx}: name="{val}", desc="{desc_val}"')
                        for fr_idx, (fr_class, fr_nat) in field_method_refs.items():
                            if fr_nat == nat_idx:
                                class_name_idx = class_refs.get(fr_class, 0)
                                class_name = utf8_entries.get(class_name_idx, '???')
                                print(f'    -> Methodref#{fr_idx}: owner_class="{class_name}"')
            
            # Also show all net/minecraft references in descriptors
            print(f'\nAll MC class refs in NAT descriptors:')
            for nat_idx, (name_idx, desc_idx) in name_and_type.items():
                desc_val = utf8_entries.get(desc_idx, '')
                matches = list(re.finditer(r'L([^;]+);', desc_val))
                for m in matches:
                    cls = m.group(1)
                    if 'minecraft' in cls or 'net/minecraft' in cls:
                        name_val = utf8_entries.get(name_idx, '???')
                        print(f'  NAT#{nat_idx}: name="{name_val}", desc="{desc_val}" -> "{cls}"')
