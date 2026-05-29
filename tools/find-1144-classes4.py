import zipfile
jar = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.14.4-28.2.30\snapshot\20190601-1.14.2\recompiled.jar'
with zipfile.ZipFile(jar) as z:
    # List ALL classes in net.minecraft.network and net.minecraft.client.network
    for name in sorted(z.namelist()):
        if name.endswith('.class') and '$' not in name:
            if 'net/minecraft/network/' in name or 'net/minecraft/client/network/' in name:
                fqn = name.replace('/', '.').replace('.class', '')
                print(f"NET: {fqn}")
    # List world package
    for name in sorted(z.namelist()):
        if name.endswith('.class') and '$' not in name and 'net/minecraft/world/' in name:
            fqn = name.replace('/', '.').replace('.class', '')
            if 'WorldClient' in fqn or 'ClientWorld' in fqn:
                print(f"WORLD: {fqn}")
    # List util.text
    for name in sorted(z.namelist()):
        if name.endswith('.class') and '$' not in name and 'util/text/' in name:
            fqn = name.replace('/', '.').replace('.class', '')
            print(f"TEXT: {fqn}")
