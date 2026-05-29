import zipfile, struct

regular = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0.jar'
fatjar = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs\msfriends-forge-1.11.2-0.1.0-all.jar'
deployed = r'C:\Users\xjh37\AppData\Roaming\.minecraft\mods\msfriends-forge-1.11.2-0.1.0.jar'

z1 = zipfile.ZipFile(regular)
z2 = zipfile.ZipFile(fatjar)
z3 = zipfile.ZipFile(deployed)

# Compare MsfFriendsForge.class across all three
for cls_name in ['dev/msf/friends/MsfFriendsForge.class', 'dev/msf/friends/bridge/HeadlessMinecraftBridge1112.class']:
    d1 = z1.read(cls_name)
    d2 = z2.read(cls_name)
    d3 = z3.read(cls_name)
    
    print(f'\n=== {cls_name} ===')
    print(f'Regular jar: {len(d1)} bytes, first 40: {d1[:40].hex()}')
    print(f'Fat jar:     {len(d2)} bytes, first 40: {d2[:40].hex()}')
    print(f'Deployed:    {len(d3)} bytes, first 40: {d3[:40].hex()}')
    print(f'Regular == FatJar: {d1 == d2}')
    print(f'Regular == Deployed: {d1 == d3}')
    print(f'FatJar == Deployed: {d2 == d3}')

    # Search for SRG names (func_ or field_) vs MCP names
    import re
    text1 = ''.join(chr(b) if 32 <= b < 127 else '\0' for b in d1)
    text2 = ''.join(chr(b) if 32 <= b < 127 else '\0' for b in d2)
    
    srg1 = set(re.findall(r'func_\d+[a-z]*', text1))
    srg2 = set(re.findall(r'func_\d+[a-z]*', text2))
    field1 = set(re.findall(r'field_\d+[a-z]*', text1))
    field2 = set(re.findall(r'field_\d+[a-z]*', text2))
    
    print(f'Regular SRG funcs: {srg1}')
    print(f'FatJar SRG funcs: {srg2}')
    print(f'Regular SRG fields: {field1}')
    print(f'FatJar SRG fields: {field2}')

z1.close()
z2.close()
z3.close()
