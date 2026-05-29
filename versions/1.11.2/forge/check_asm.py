import zipfile

# Check ASM version in the forge jar
forge_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588.jar'
z = zipfile.ZipFile(forge_jar)
print("=== Forge jar: looking for ASM and BlamingTransformer ===")
for name in z.namelist():
    if 'Blaming' in name or 'blaming' in name:
        print(f'  Found: {name}')
    if 'ClassReader' in name:
        print(f'  Found: {name}')
    if 'asm' in name.lower() and name.endswith('.class'):
        print(f'  ASM: {name}')
z.close()

# Check launchwrapper jar
lw_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar'
import os
if os.path.exists(lw_jar):
    z = zipfile.ZipFile(lw_jar)
    print("\n=== Launchwrapper jar ===")
    for name in z.namelist():
        if 'Transform' in name or 'Class' in name:
            print(f'  Found: {name}')
    z.close()

# Check ASM jar
asm_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\org\ow2\asm\asm-all\5.0.3\asm-all-5.0.3.jar'
if os.path.exists(asm_jar):
    z = zipfile.ZipFile(asm_jar)
    print("\n=== ASM jar ===")
    for name in z.namelist():
        if 'ClassReader' in name:
            print(f'  Found: {name}')
            data = z.read(name)
            # Find version constant
            import struct
            # ASM version is typically in a static field
            text = ''.join(chr(b) if 32 <= b < 127 else '.' for b in data)
            if 'ASM' in text:
                import re
                versions = re.findall(r'ASM\d+', text)
                print(f'    ASM versions found: {versions}')
    z.close()
