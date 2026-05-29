import json, os

# Check what's in the .minecraft version directory
ver_dir = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.11.2-forge-13.20.1.2588'
if os.path.exists(ver_dir):
    print(f"Version directory: {ver_dir}")
    for f in os.listdir(ver_dir):
        print(f"  {f} ({os.path.getsize(os.path.join(ver_dir, f))} bytes)")
    
    # Read the version JSON
    json_path = os.path.join(ver_dir, '1.11.2-forge-13.20.1.2588.json')
    with open(json_path, 'r') as f:
        vj = json.load(f)
    
    print(f"\nid: {vj.get('id')}")
    print(f"mainClass: {vj.get('mainClass')}")
    print(f"inheritsFrom: {vj.get('inheritsFrom')}")
    print(f"jar: {vj.get('jar')}")
    
    args = vj.get('minecraftArguments', '')
    print(f"minecraftArguments: ...{args[-200:]}")
    
    # Check libraries for forge
    libs = vj.get('libraries', [])
    forge_lib = None
    for lib in libs:
        name = lib.get('name', '')
        if 'forge' in name.lower() and 'minecraft' not in name.lower():
            forge_lib = lib
            print(f"\nForge lib: {name}")
            print(f"  URL: {lib.get('url', 'default')}")
    
    # Check for tweakClass
    print(f"\ntweakClass in args: {'tweakClass' in args}")
    print(f"launchwrapper in args: {'launchwrapper' in args or 'Launch' in args}")

# Now check the Forge universal jar for binary patches data
import zipfile
forge_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588-universal.jar'
if os.path.exists(forge_jar):
    z = zipfile.ZipFile(forge_jar)
    entries = z.namelist()
    
    # Look for patch data, binpatches, or FML data
    patch_entries = [e for e in entries if 'patch' in e.lower() or 'binpatch' in e.lower() or 'fml' in e.lower() or 'deobf' in e.lower()]
    print(f"\n=== Forge universal jar: patch/FML entries ===")
    for e in sorted(patch_entries):
        info = z.getinfo(e)
        print(f"  {e} ({info.file_size} bytes)")
    
    # Check for data/ directory
    data_entries = [e for e in entries if e.startswith('data/') or e.startswith('assets/')]
    print(f"\n=== data/assets entries ===")
    for e in sorted(data_entries):
        info = z.getinfo(e)
        print(f"  {e} ({info.file_size} bytes)")
    
    # Look for any directory structures  
    dirs = set()
    for e in entries:
        parts = e.split('/')
        if len(parts) > 1:
            dirs.add(parts[0])
    print(f"\n=== Top-level directories ===")
    for d in sorted(dirs):
        count = len([e for e in entries if e.startswith(d + '/')])
        print(f"  {d}/ ({count} entries)")
    
    z.close()
