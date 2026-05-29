import zipfile, struct, re

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

def read_u2(data, pos):
    return (data[pos] << 8) | data[pos+1]

def analyze_class(class_bytes):
    pos = 10
    cp_count = read_u2(class_bytes, pos)
    pos += 2
    
    utf8_entries = {}
    class_refs = {}
    name_and_type = {}
    field_method_refs = {}
    
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
            class_refs[i] = read_u2(class_bytes, pos)
            pos += 2
        elif tag == 8: pos += 2
        elif tag in (9, 10, 11):
            ci = read_u2(class_bytes, pos)
            ni = read_u2(class_bytes, pos+2)
            field_method_refs[i] = (ci, ni)
            pos += 4
        elif tag == 12:
            ni = read_u2(class_bytes, pos)
            di = read_u2(class_bytes, pos+2)
            name_and_type[i] = (ni, di)
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
            
            # Print ALL UTF8 entries that look interesting
            print("ALL UTF8 entries:")
            for idx, val in sorted(utf8_entries.items()):
                if val and not val.startswith('java/') and not val.startswith('org/spongepowered'):
                    print(f"  #{idx}: \"{val}\"")
            
            # Show all NAT entries with descriptors containing net/minecraft
            print("\nNameAndType entries with MC refs:")
            for nat_idx, (name_idx, desc_idx) in sorted(name_and_type.items()):
                name_val = utf8_entries.get(name_idx, '???')
                desc_val = utf8_entries.get(desc_idx, '???')
                if 'net/minecraft' in name_val or 'net/minecraft' in desc_val or 'connection' in name_val.lower() or 'render' in name_val.lower():
                    print(f"  NAT#{nat_idx}: name=\"{name_val}\" desc=\"{desc_val}\"")
