import zipfile, os

build_jar = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0.jar'
deployed_jar = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'

print('Build jar exists:', os.path.exists(build_jar))
print('Deployed jar exists:', os.path.exists(deployed_jar))

if os.path.exists(build_jar):
    z1 = zipfile.ZipFile(build_jar)
    z2 = zipfile.ZipFile(deployed_jar)
    
    all_classes = set()
    for name in z1.namelist():
        if name.endswith('.class'):
            all_classes.add(name)
    for name in z2.namelist():
        if name.endswith('.class'):
            all_classes.add(name)
    
    for name in sorted(all_classes):
        b1 = z1.read(name) if name in z1.namelist() else None
        b2 = z2.read(name) if name in z2.namelist() else None
        if b1 and b2:
            if b1 == b2:
                print(f'IDENTICAL: {name} ({len(b1)})')
            else:
                print(f'DIFFERENT: {name} (build={len(b1)} deployed={len(b2)})')
                # Check first 20 bytes
                print(f'  Build:    {b1[:20].hex()}')
                print(f'  Deployed: {b2[:20].hex()}')
        elif b1 and not b2:
            print(f'ONLY IN BUILD: {name}')
        else:
            print(f'ONLY IN DEPLOYED: {name}')
    
    z1.close()
    z2.close()
