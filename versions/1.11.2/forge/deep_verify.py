import zipfile, struct, os

# Create a minimal test mod jar that we know is valid
# First, check the existing MsfFriendsForge class
jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'
z = zipfile.ZipFile(jar_path)

print("=== Detailed analysis of MsfFriendsForge.class ===")
data = z.read('dev/msf/friends/MsfFriendsForge.class')
print(f"File size: {len(data)} bytes")

# Parse the class file structure
magic = struct.unpack('>I', data[0:4])[0]
minor = struct.unpack('>H', data[4:6])[0]
major = struct.unpack('>H', data[6:8])[0]
cp_count = struct.unpack('>H', data[8:10])[0]
access_flags = struct.unpack('>H', data[10:12])[0]
this_class = struct.unpack('>H', data[12:14])[0]
super_class = struct.unpack('>H', data[14:16])[0]
iface_count = struct.unpack('>H', data[16:18])[0]

print(f"Magic: 0x{magic:08x}")
print(f"Version: {minor}.{major}")
print(f"Constant pool count: {cp_count}")
print(f"Access flags: 0x{access_flags:04x}")
print(f"This class: #{this_class}")
print(f"Super class: #{super_class}")
print(f"Interface count: {iface_count}")

# Parse constant pool fully
offset = 10
errors = []
for i in range(1, cp_count):
    if offset >= len(data):
        errors.append(f"Ran out of data at CP#{i}, offset={offset}")
        break
    tag = data[offset]
    if tag == 1:  # Utf8
        length = struct.unpack('>H', data[offset+1:offset+3])[0]
        if offset + 3 + length > len(data):
            errors.append(f"Utf8 extends past end at CP#{i}")
            break
        offset += 3 + length
    elif tag == 7:  # Class
        offset += 3
    elif tag == 8:  # String
        offset += 3
    elif tag in (9, 10, 11, 12):  # Fieldref, Methodref, InterfaceMethodref, NameAndType
        offset += 5
    elif tag in (3, 4):  # Integer, Float
        offset += 5
    elif tag in (5, 6):  # Long, Double
        offset += 9
        i += 1  # takes 2 entries
    elif tag == 15:  # MethodHandle
        offset += 4
    elif tag == 16:  # MethodType
        offset += 3
    elif tag == 18:  # InvokeDynamic
        offset += 5
    else:
        errors.append(f"Unknown tag {tag} at CP#{i}, offset={offset}")
        break

if errors:
    print(f"\n*** ERRORS ***")
    for e in errors:
        print(f"  {e}")
else:
    print(f"\nConstant pool parsed successfully! Final offset: {offset}")

# Also check the mcmod.info
mcmod = z.read('mcmod.info')
print(f"\n=== mcmod.info ===")
print(mcmod.decode('utf-8'))

z.close()

# Now check the Forge version jar to see if the binary patches are there
forge_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588.jar'
if os.path.exists(forge_jar):
    z = zipfile.ZipFile(forge_jar)
    print(f"\n=== Forge jar contents (first 30) ===")
    for name in sorted(z.namelist())[:30]:
        info = z.getinfo(name)
        print(f"  {name} ({info.file_size})")
    
    # Check for binary patches
    for name in z.namelist():
        if 'patch' in name.lower() or 'binpatch' in name.lower():
            print(f"  PATCH: {name}")
    z.close()
