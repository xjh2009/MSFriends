import zipfile, os

# Compare the version jar and the library jar
ver_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.11.2-forge-13.20.1.2588\1.11.2-forge-13.20.1.2588.jar'
lib_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588.jar'

print(f"Version jar: {os.path.getsize(ver_jar)} bytes")
print(f"Library jar: {os.path.getsize(lib_jar)} bytes")

# Compare CRC of both
import hashlib
with open(ver_jar, 'rb') as f:
    vh = hashlib.md5(f.read()).hexdigest()
with open(lib_jar, 'rb') as f:
    lh = hashlib.md5(f.read()).hexdigest()
    
print(f"Version jar MD5: {vh}")
print(f"Library jar MD5: {lh}")
print(f"Same file: {vh == lh}")

# Check binpatches.pack.lzma in both
zv = zipfile.ZipFile(ver_jar)
zl = zipfile.ZipFile(lib_jar)

vbp = zv.read('binpatches.pack.lzma')
lbp = zl.read('binpatches.pack.lzma')
print(f"\nbinpatches.pack.lzma in version jar: {len(vbp)} bytes")
print(f"binpatches.pack.lzma in library jar: {len(lbp)} bytes")
print(f"Same content: {vbp == lbp}")

# Check all entries in version jar that are NOT in library jar
ver_entries = set(zv.namelist())
lib_entries = set(zl.namelist())
only_in_ver = ver_entries - lib_entries
only_in_lib = lib_entries - ver_entries
print(f"\nOnly in version jar: {len(only_in_ver)}")
for e in sorted(only_in_ver)[:5]:
    print(f"  {e}")
print(f"Only in library jar: {len(only_in_lib)}")
for e in sorted(only_in_lib)[:5]:
    print(f"  {e}")

zv.close()
zl.close()

# Now check: does the universal jar have a correct ClassLoader structure?
# The key issue: does the forge universal jar have proper class hierarchy?
z = zipfile.ZipFile(ver_jar)
for name in ['net/minecraftforge/classloading/FMLForgePlugin.class']:
    data = z.read(name)
    magic = int.from_bytes(data[0:4], 'big')
    print(f"\n{name}: magic=0x{magic:08x}, size={len(data)}")
    
# Check for version info in the jar
for name in z.namelist():
    if name.endswith('version.properties') or name.endswith('version.txt'):
        print(f"\nVersion file: {name}")
        print(z.read(name).decode('utf-8')[:200])
    if 'forgeversion' in name.lower() or 'fmlversion' in name.lower():
        print(f"Version file: {name}")

z.close()
