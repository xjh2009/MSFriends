import zipfile, struct, os, sys

jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'

z = zipfile.ZipFile(jar_path)
for info in z.infolist():
    data = z.read(info.filename)
    if info.filename.endswith('.class'):
        if len(data) < 4:
            print(f'CORRUPT: {info.filename} too small ({len(data)})')
            continue
        magic = struct.unpack('>I', data[0:4])[0]
        if magic != 0xCAFEBABE:
            print(f'CORRUPT: {info.filename} bad magic 0x{magic:08x}')
        else:
            print(f'OK: {info.filename} ({len(data)} bytes) magic=0x{magic:08x}')
    else:
        print(f'OTHER: {info.filename} ({len(data)} bytes)')

# Check if there are any duplicate entries
names = z.namelist()
seen = {}
for n in names:
    if n in seen:
        print(f'DUPLICATE ENTRY: {n}')
    seen[n] = True

# Try reading the class with javap equivalent
print('\n--- Checking MsfFriendsForge.class constant pool tag integrity ---')
data = z.read('dev/msf/friends/MsfFriendsForge.class')
offset = 10  # Start of constant pool
cp_count = struct.unpack('>H', data[8:10])[0]
print(f'Constant pool count: {cp_count}')
valid = True
for i in range(1, cp_count):
    if offset >= len(data):
        print(f'  ERROR: ran out of data at CP entry {i}, offset {offset}')
        valid = False
        break
    tag = data[offset]
    tags = {1:'Utf8',3:'Integer',4:'Float',5:'Long',6:'Double',7:'Class',8:'String',9:'Fieldref',10:'Methodref',11:'InterfaceMethodref',12:'NameAndType',15:'MethodHandle',16:'MethodType',18:'InvokeDynamic'}
    if tag not in tags:
        print(f'  ERROR: unknown tag {tag} at offset {offset} for CP entry {i}')
        valid = False
        break
    name = tags[tag]
    if tag == 1:  # Utf8
        length = struct.unpack('>H', data[offset+1:offset+3])[0]
        offset += 3 + length
    elif tag in (5, 6):  # Long, Double
        offset += 9
        i += 1
    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
        offset += 5
    elif tag in (7, 8, 16, 19, 20):
        offset += 3
    elif tag == 15:
        offset += 4
    else:
        offset += 3

if valid:
    print('  Constant pool parsed successfully!')

# Extract and check a class independently
print('\n--- Extracting MsfFriendsForge.class to verify independently ---')
data = z.read('dev/msf/friends/MsfFriendsForge.class')
extracted = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\MsfFriendsForge.class'
with open(extracted, 'wb') as f:
    f.write(data)
print(f'Extracted {len(data)} bytes to {extracted}')

z.close()
