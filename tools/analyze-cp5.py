import zipfile, os, struct

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

TAG_NAMES = {1:'Utf8',3:'Integer',4:'Float',5:'Long',6:'Double',7:'Class',8:'String',9:'Fieldref',10:'Methodref',11:'InterfaceMethodref',12:'NameAndType',15:'MethodHandle',16:'MethodType',17:'Dynamic',18:'InvokeDynamic',19:'Module',20:'Package'}

with zipfile.ZipFile(jar_path) as z:
    for target in ['ClientLoginMixin.class', 'ServerLoginMixin.class', 'OnlineOptionsScreenMixin.class']:
        for name in z.namelist():
            if name.endswith(target):
                data = z.read(name)
                # Parse class file: magic(4) + minor(2) + major(2) = 8 bytes, then cp_count(u2)
                cp_count = struct.unpack('>H', data[8:10])[0]
                print(f'\n=== {name} (size={len(data)}, cp_count={cp_count}) ===')
                
                pos = 10
                utf8_map = {}
                class_refs = {}
                nat_map = {}
                fmrefs = {}
                
                i = 1
                while i < cp_count and pos < len(data):
                    tag = data[pos]
                    pos += 1
                    if tag == 1:
                        length = struct.unpack('>H', data[pos:pos+2])[0]
                        pos += 2
                        val = data[pos:pos+length].decode('utf-8', errors='replace')
                        utf8_map[i] = val
                        pos += length
                    elif tag in (3, 4): pos += 4
                    elif tag in (5, 6): pos += 8; i += 1
                    elif tag == 7:
                        class_refs[i] = struct.unpack('>H', data[pos:pos+2])[0]
                        pos += 2
                    elif tag == 8: pos += 2
                    elif tag in (9, 10, 11):
                        ci = struct.unpack('>H', data[pos:pos+2])[0]
                        ni = struct.unpack('>H', data[pos+2:pos+4])[0]
                        fmrefs[i] = (ci, ni, tag)
                        pos += 4
                    elif tag == 12:
                        ni = struct.unpack('>H', data[pos:pos+2])[0]
                        di = struct.unpack('>H', data[pos+2:pos+4])[0]
                        nat_map[i] = (ni, di)
                        pos += 4
                    elif tag == 15: pos += 3
                    elif tag == 16: pos += 2
                    elif tag in (17, 18): pos += 4
                    elif tag in (19, 20): pos += 2
                    else:
                        print(f'  Unknown tag {tag} at pos {pos-1}')
                        break
                    i += 1
                
                print(f'  Parsed {i-1} CP entries, {len(utf8_map)} utf8, {len(nat_map)} NAT, {len(fmrefs)} FMrefs')
                
                # Show ALL UTF8 entries
                print('  ALL UTF8 entries:')
                for idx in sorted(utf8_map.keys()):
                    val = utf8_map[idx]
                    print(f'    #{idx}: "{val}"')
                
                # Find 'connection' or 'render' in UTF8
                for needle in ['connection', 'render', 'allKeys', 'getConnection']:
                    matches = [(idx, val) for idx, val in utf8_map.items() if needle in val]
                    if matches:
                        print(f'  UTF8 containing "{needle}":')
                        for idx, val in matches:
                            print(f'    #{idx}: "{val}"')
                            # Find NATs referencing this
                            for nat_idx, (name_idx, desc_idx) in nat_map.items():
                                if name_idx == idx:
                                    desc = utf8_map.get(desc_idx, '???')
                                    print(f'      NAT#{nat_idx}: name=#{name_idx}, desc=#{desc_idx} -> "{desc}"')
                                    # Find FMrefs referencing this NAT
                                    for fm_idx, (ci, ni, fm_tag) in fmrefs.items():
                                        if ni == nat_idx:
                                            cls_idx = class_refs.get(ci, 0)
                                            cls = utf8_map.get(cls_idx, '???')
                                            tag_name = TAG_NAMES.get(fm_tag, f'tag{fm_tag}')
                                            print(f'        {tag_name}#{fm_idx}: owner="{cls}"')
                
                break
