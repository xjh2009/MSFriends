import zipfile, json

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

with zipfile.ZipFile(jar_path) as z:
    for name in z.namelist():
        if 'refmap' in name:
            print(f'=== {name} ===')
            data = z.read(name).decode('utf-8')
            print(data[:5000])
            print('...')
