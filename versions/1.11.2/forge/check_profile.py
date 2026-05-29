import zipfile, json

installer = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\forge-installer.jar'
z = zipfile.ZipFile(installer)

# Check for install_profile.json
if 'install_profile.json' in z.namelist():
    data = z.read('install_profile.json')
    profile = json.loads(data)
    print("=== install_profile.json ===")
    print(json.dumps(profile, indent=2)[:2000])

# Check for version.json  
if 'version.json' in z.namelist():
    vdata = z.read('version.json')
    vj = json.loads(vdata)
    print("\n=== version.json ===")
    print(json.dumps(vj, indent=2)[:5000])

z.close()
