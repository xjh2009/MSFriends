import zipfile, struct

# Check the mod jar structure
jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'
z = zipfile.ZipFile(jar_path)

print("=== Mod jar entries ===")
for name in z.namelist():
    info = z.getinfo(name)
    print(f"  {name} ({info.file_size} bytes, method={info.compress_type})")

# Check each .class file
print("\n=== Class file analysis ===")
for name in z.namelist():
    if name.endswith('.class'):
        data = z.read(name)
        magic = struct.unpack('>I', data[0:4])[0]
        minor = struct.unpack('>H', data[4:6])[0]
        major = struct.unpack('>H', data[6:8])[0]
        compress_ok = "OK" if magic == 0xCAFEBABE else "BAD"
        print(f"  {name}: magic=0x{magic:08x} ({compress_ok}), version={major}.{minor}, size={len(data)}")
        
        # Check if the class extends/uses any problematic types
        # Parse constant pool
        cp_count = struct.unpack('>H', data[8:10])[0]
        offset = 10
        for i in range(1, cp_count):
            tag = data[offset]
            if tag == 1:  # Utf8
                length = struct.unpack('>H', data[offset+1:offset+3])[0]
                text = data[offset+3:offset+3+length].decode('utf-8', errors='replace')
                if 'java/lang' in text or 'net/minecraft' in text or 'net/minecraftforge' in text:
                    pass  # normal
                elif 'dev/msf' in text:
                    pass  # our own classes
                offset += 3 + length
            elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
                offset += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # Integer, Float, Field, Method, IFMethod, NameAndType, Dynamic, InvokeDynamic
                offset += 5
            elif tag in (5, 6):  # Long, Double
                offset += 9
                i += 1
            elif tag == 15:  # MethodHandle
                offset += 4
            else:
                print(f"    *** Unknown CP tag {tag} at #{i} offset {offset}")
                break

# Now check if BlamingTransformer might receive compressed data
# The compress_type in zip should be handled properly by Java's ZipInputStream
# But let's verify the decompressed data matches what we expect
print("\n=== Decompression verification ===")
for name in z.namelist():
    if name == 'dev/msf/friends/MsfFriendsForge.class':
        raw_info = z.getinfo(name)
        decompressed = z.read(name)
        print(f"Compress type: {raw_info.compress_type} (0=stored, 8=deflated)")
        print(f"Compressed size: {raw_info.compress_size}")
        print(f"Uncompressed size: {raw_info.file_size}")
        print(f"Decompressed length: {len(decompressed)}")
        print(f"First 4 bytes: {decompressed[0:4].hex()}")
        
z.close()
