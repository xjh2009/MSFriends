import zipfile, os

cache_dir = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches'
found = False
for root, dirs, files in os.walk(cache_dir):
    for f in files:
        if f.endswith('.jar'):
            path = os.path.join(root, f)
            size = os.path.getsize(path)
            try:
                with zipfile.ZipFile(path, 'r') as z:
                    z.testzip()
            except:
                found = True
                print(f'CORRUPT {size:>10} bytes - {path}')

if not found:
    print('No corrupt jars found')
