import os, zipfile

# Check ALL jars in the forge cache
forge_dir = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge'
for root, dirs, files in os.walk(forge_dir):
    for f in files:
        if f.endswith('.jar'):
            path = os.path.join(root, f)
            size = os.path.getsize(path)
            try:
                with zipfile.ZipFile(path, 'r') as z:
                    main = z.manifest
                    if main:
                        print(f'VALID {size:>10} Main-Class: {main.get("Main-Class", "N/A")} - {os.path.relpath(path, forge_dir)}')
                    else:
                        entries = len(z.namelist())
                        print(f'VALID {size:>10} entries={entries} - {os.path.relpath(path, forge_dir)}')
            except Exception as e:
                print(f'CORRUPT {size:>10} - {os.path.relpath(path, forge_dir)} - {e}')

# Also check the specific rename output area
rename_paths = [
    r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\.global\mcp\1.14.4-20190829.143755',
]
for rp in rename_paths:
    if os.path.exists(rp):
        print(f'\n--- 1.14.4 forge cache ---')
        for root, dirs, files in os.walk(rp):
            for f in files:
                path = os.path.join(root, f)
                size = os.path.getsize(path)
                rel = os.path.relpath(path, rp)
                print(f'  {size:>10} - {rel}')
    else:
        print(f'{rp} does not exist')
