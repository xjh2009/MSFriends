import zipfile, struct

# Compare the regular jar vs fat jar
regular = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0.jar'
fatjar = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0-all.jar'

print("=== Regular jar ===")
z = zipfile.ZipFile(regular)
for info in z.infolist():
    if info.filename.endswith('.class'):
        data = z.read(info.filename)
        magic = struct.unpack('>I', data[0:4])[0]
        major = struct.unpack('>H', data[6:8])[0]
        print(f'  {info.filename} ({len(data)} bytes) major={major} magic=0x{magic:08x}')
# Check MANIFEST
print(f'  MANIFEST: {z.read("META-INF/MANIFEST.MF").decode("utf-8")}')
z.close()

print()
print("=== Fat jar ===")
z = zipfile.ZipFile(fatjar)
for info in z.infolist():
    if info.filename.endswith('.class'):
        data = z.read(info.filename)
        magic = struct.unpack('>I', data[0:4])[0]
        major = struct.unpack('>H', data[6:8])[0]
        print(f'  {info.filename} ({len(data)} bytes) major={major} magic=0x{magic:08x}')
print(f'  MANIFEST: {z.read("META-INF/MANIFEST.MF").decode("utf-8")}')
z.close()

print()
print("=== Comparing MsfFriendsForge.class between jars ===")
z1 = zipfile.ZipFile(regular)
z2 = zipfile.ZipFile(fatjar)
d1 = z1.read('dev/msf/friends/MsfFriendsForge.class')
d2 = z2.read('dev/msf/friends/MsfFriendsForge.class')
print(f'Regular: {len(d1)} bytes')
print(f'FatJar:  {len(d2)} bytes')
print(f'Identical: {d1 == d2}')

# Check the first 100 bytes of each
print(f'Regular first 50 bytes hex: {d1[:50].hex()}')
print(f'FatJar first 50 bytes hex:  {d2[:50].hex()}')
z1.close()
z2.close()
