import zipfile, struct, os

forge_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588.jar'
z = zipfile.ZipFile(forge_jar)

# Extract BlamingTransformer to analyze
bt_data = z.read('net/minecraftforge/fml/common/asm/transformers/BlamingTransformer.class')
print(f"BlamingTransformer class size: {len(bt_data)}")

# Also check for SideTransformer
st_data = z.read('net/minecraftforge/fml/common/asm/transformers/SideTransformer.class')
print(f"SideTransformer class size: {len(st_data)}")

# Check the ASM bundled in forge
asm_entries = [n for n in z.namelist() if 'asm/' in n and n.endswith('.class')]
print(f"\nASM classes in forge jar: {len(asm_entries)}")
for e in asm_entries[:10]:
    print(f"  {e}")

# Check if there's a separate ClassReader
for name in z.namelist():
    if 'ClassReader' in name:
        data = z.read(name)
        print(f"\nClassReader: {name} ({len(data)} bytes)")

# List all transformer classes
print("\n=== Transformer classes ===")
for name in z.namelist():
    if 'transform' in name.lower() and name.endswith('.class'):
        print(f"  {name}")

z.close()

# Check if there's a repackaged ASM in launchwrapper
lw_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar'
if os.path.exists(lw_jar):
    z = zipfile.ZipFile(lw_jar)
    asm_lw = [n for n in z.namelist() if 'asm' in n.lower() and n.endswith('.class')]
    print(f"\n=== ASM classes in launchwrapper: {len(asm_lw)} ===")
    for e in asm_lw:
        print(f"  {e}")
    
    # Check for ClassReader in launchwrapper
    for name in z.namelist():
        if 'ClassReader' in name:
            data = z.read(name)
            print(f"\nClassReader in LW: {name} ({len(data)} bytes)")
    z.close()

# Also check the ASM standalone jar
asm_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\org\ow2\asm\asm-all\5.0.3\asm-all-5.0.3.jar'
if os.path.exists(asm_jar):
    z = zipfile.ZipFile(asm_jar)
    cr_data = z.read('org/objectweb/asm/ClassReader.class')
    print(f"\n=== ASM ClassReader from asm-all-5.0.3: {len(cr_data)} bytes ===")
    
    # Check ASM version constant
    text = ''.join(chr(b) if 32 <= b < 127 else '\0' for b in cr_data)
    import re
    versions = re.findall(r'ASM\d[a-z]?', text)
    print(f"ASM version strings found: {set(versions)}")
    z.close()
