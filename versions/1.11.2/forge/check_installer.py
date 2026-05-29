import zipfile, json, os

installer = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\forge-installer.jar'
z = zipfile.ZipFile(installer)

print("=== Installer contents ===")
for name in z.namelist():
    info = z.getinfo(name)
    print(f"  {name} ({info.file_size})")

# Read install_profile.json
data = z.read('install_profile.json')
profile = json.loads(data)
print(f"\n=== install_profile.json ===")
print(json.dumps(profile, indent=2)[:3000])

# Check for version.json
if 'version.json' in z.namelist():
    vdata = z.read('version.json')
    vj = json.loads(vdata)
    print(f"\n=== version.json ===")
    print(json.dumps(vj, indent=2)[:3000])

z.close()
