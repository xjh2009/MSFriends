import zipfile
jar = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.14.4-28.2.30\snapshot\20190601-1.14.2\recompiled.jar'
with zipfile.ZipFile(jar) as z:
    # List ALL classes in client/gui package
    gui_classes = []
    for name in sorted(z.namelist()):
        if name.endswith('.class') and 'net/minecraft/client/gui/' in name and name.count('/') == 6:
            cls = name.split('/')[-1].replace('.class', '')
            fqn = name.replace('/', '.').replace('.class', '')
            gui_classes.append((cls, fqn))
    
    print(f"Total GUI classes: {len(gui_classes)}")
    for cls, fqn in gui_classes:
        print(f"  {fqn}")
    
    # Also search for screens package
    print("\n--- screens package ---")
    for name in sorted(z.namelist()):
        if name.endswith('.class') and 'screens/' in name:
            fqn = name.replace('/', '.').replace('.class', '')
            print(f"  {fqn}")
    
    # Also search for login/network packages
    print("\n--- login/network packages ---")
    for name in sorted(z.namelist()):
        if name.endswith('.class') and ('login' in name or 'play' in name) and 'net/minecraft' in name:
            cls = name.split('/')[-1].replace('.class', '')
            fqn = name.replace('/', '.').replace('.class', '')
            print(f"  {fqn}")
