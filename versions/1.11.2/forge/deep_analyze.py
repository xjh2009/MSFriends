import zipfile, struct

jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'
z = zipfile.ZipFile(jar_path)

# Deep-dive into MsfFriendsBoot1112.class to understand corruption
data = z.read('dev/msf/friends/MsfFriendsBoot1112.class')
print(f"Size: {len(data)} bytes")

# Check surrounding bytes at the corruption point (offset 5469)
print(f"\nBytes around offset 5469:")
for i in range(5460, min(5480, len(data))):
    print(f"  [{i}] = 0x{data[i]:02x} ({data[i]})")

# Parse CP entries carefully around #340-#345
offset = 10
for i in range(1, 360):
    if offset >= len(data):
        print(f"\n*** Ran out of data at CP#{i}, offset={offset}, fileLen={len(data)}")
        break
    tag = data[offset]
    
    if tag == 0:
        print(f"\n*** ZERO TAG at CP#{i}, offset={offset}")
        print(f"  Surrounding bytes: {data[max(0,offset-5):offset+5].hex()}")
        # Check what's before
        break
    
    if tag == 1:  # Utf8
        length = struct.unpack('>H', data[offset+1:offset+3])[0]
        if offset + 3 + length > len(data):
            print(f"\n*** Utf8 overflow at CP#{i}")
            break
        if i >= 338 and i <= 348:
            text = data[offset+3:offset+3+length].decode('utf-8', errors='replace')
            print(f"  CP#{i}: Utf8({length}) = '{text[:80]}'")
        offset += 3 + length
    elif tag == 7:  # Class
        if i >= 338 and i <= 348:
            idx = struct.unpack('>H', data[offset+1:offset+3])[0]
            print(f"  CP#{i}: Class -> #{idx}")
        offset += 3
    elif tag == 8:  # String
        if i >= 338 and i <= 348:
            idx = struct.unpack('>H', data[offset+1:offset+3])[0]
            print(f"  CP#{i}: String -> #{idx}")
        offset += 3
    elif tag in (9, 10, 11, 12):  # Fieldref, Methodref, InterfaceMethodref, NameAndType
        if i >= 338 and i <= 348:
            v1 = struct.unpack('>H', data[offset+1:offset+3])[0]
            v2 = struct.unpack('>H', data[offset+3:offset+5])[0]
            name = {9:'Field', 10:'Method', 11:'IFMethod', 12:'NameAndType'}[tag]
            print(f"  CP#{i}: {name} -> #{v1},#{v2}")
        offset += 5
    elif tag in (3, 4):  # Integer, Float
        offset += 5
    elif tag in (5, 6):  # Long, Double
        offset += 9
        i += 1  # takes 2 slots
    elif tag == 15:  # MethodHandle
        if i >= 338 and i <= 348:
            ref_kind = data[offset+1]
            ref_idx = struct.unpack('>H', data[offset+2:offset+4])[0]
            print(f"  CP#{i}: MethodHandle kind={ref_kind} -> #{ref_idx}")
        offset += 4
    elif tag == 16:  # MethodType
        offset += 3
    elif tag in (17, 18):  # Dynamic, InvokeDynamic
        offset += 5
    elif tag in (19, 20):  # Module, Package (Java 9+)
        offset += 3
    else:
        print(f"*** Unknown tag {tag} at CP#{i}, offset={offset}")
        break

z.close()
