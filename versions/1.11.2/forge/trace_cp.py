import zipfile, struct

# Analyze exactly what's happening around the corruption
jar_path = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0-all.jar'
z = zipfile.ZipFile(jar_path)
data = z.read('dev/msf/friends/MsfFriendsBoot1112.class')
z.close()

print(f"Total size: {len(data)} bytes, constant pool count: {struct.unpack('>H', data[8:10])[0]}")

# Parse all CP entries carefully
offset = 10
cp_count = struct.unpack('>H', data[8:10])[0]
entries = {}
for i in range(1, cp_count):
    tag = data[offset]
    if tag == 0:
        print(f"\n*** ZERO TAG at CP#{i}, offset={offset}")
        print(f"  Previous entry was CP#{i-1}")
        print(f"  Bytes 20 before: {data[offset-20:offset].hex()}")
        print(f"  Bytes 20 after: {data[offset:offset+20].hex()}")
        
        # Let's check if there's a pattern - maybe the Utf8 length was wrong
        # Go back to CP#{i-1} and re-parse
        prev_offset = entries.get(i-1, (None, None))[1]
        if prev_offset is not None:
            prev_tag = data[prev_offset]
            print(f"\n  CP#{i-1} at offset {prev_offset}: tag={prev_tag}")
            if prev_tag == 1:  # Utf8
                length = struct.unpack('>H', data[prev_offset+1:prev_offset+3])[0]
                text = data[prev_offset+3:prev_offset+3+length]
                print(f"    Utf8({length}): {text.decode('utf-8', errors='replace')[:100]}")
                print(f"    End would be at {prev_offset + 3 + length}, actual tag 0 at {offset}")
                if prev_offset + 3 + length != offset:
                    print(f"    *** MISMATCH: expected end at {prev_offset + 3 + length}, got {offset}")
        break
    
    entries[i] = (tag, offset)
    
    if tag == 1:  # Utf8
        length = struct.unpack('>H', data[offset+1:offset+3])[0]
        if i >= 335:
            text = data[offset+3:offset+3+length].decode('utf-8', errors='replace')
            print(f"  CP#{i} @{offset}: Utf8({length}) = '{text[:100]}'")
        offset += 3 + length
    elif tag == 7:  # Class
        idx = struct.unpack('>H', data[offset+1:offset+3])[0]
        if i >= 335:
            print(f"  CP#{i} @{offset}: Class -> #{idx}")
        offset += 3
    elif tag == 8:  # String
        idx = struct.unpack('>H', data[offset+1:offset+3])[0]
        if i >= 335:
            print(f"  CP#{i} @{offset}: String -> #{idx}")
        offset += 3
    elif tag in (9, 10, 11, 12):
        v1 = struct.unpack('>H', data[offset+1:offset+3])[0]
        v2 = struct.unpack('>H', data[offset+3:offset+5])[0]
        name = {9:'Field', 10:'Method', 11:'IFMethod', 12:'NameAndType'}[tag]
        if i >= 335:
            print(f"  CP#{i} @{offset}: {name}({tag}) -> #{v1},#{v2}")
        offset += 5
    elif tag in (3, 4):
        offset += 5
    elif tag in (5, 6):
        if i >= 335:
            v = struct.unpack('>q' if tag == 5 else '>d', data[offset+1:offset+9])[0]
            print(f"  CP#{i} @{offset}: {'Long' if tag == 5 else 'Double'} = {v}")
        offset += 9
        i += 1  # takes 2 slots
    elif tag == 15:  # MethodHandle
        ref_kind = data[offset+1]
        ref_idx = struct.unpack('>H', data[offset+2:offset+4])[0]
        if i >= 335:
            print(f"  CP#{i} @{offset}: MethodHandle kind={ref_kind} -> #{ref_idx}")
        offset += 4
    elif tag == 16:  # MethodType
        idx = struct.unpack('>H', data[offset+1:offset+3])[0]
        if i >= 335:
            print(f"  CP#{i} @{offset}: MethodType -> #{idx}")
        offset += 3
    elif tag in (17, 18):  # Dynamic, InvokeDynamic
        v1 = struct.unpack('>H', data[offset+1:offset+3])[0]
        v2 = struct.unpack('>H', data[offset+3:offset+5])[0]
        name = 'Dynamic' if tag == 17 else 'InvokeDynamic'
        if i >= 335:
            print(f"  CP#{i} @{offset}: {name} -> #{v1},#{v2}")
        offset += 5
    else:
        print(f"  CP#{i} @{offset}: UNKNOWN tag {tag}")
        break
