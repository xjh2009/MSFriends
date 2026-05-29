import urllib.request, json, os

# Download Mojang ProGuard mapping for 1.17.1
url = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
data = json.loads(urllib.request.urlopen(url, timeout=10).read())
for v in data['versions']:
    if v['id'] == '1.17.1':
        version_url = v['url']
        print(f'1.17.1 version URL: {version_url}')
        ver_data = json.loads(urllib.request.urlopen(version_url, timeout=10).read())
        downloads = ver_data.get('downloads', {})
        client_mappings = downloads.get('client_mappings', {})
        mappings_url = client_mappings.get('url', '')
        print(f'Client mappings URL: {mappings_url}')
        
        # Download and save
        out_path = 'C:/Users/xjh37/Desktop/MSF/msf-friends-multi/build/mojang-client-1.17.1.txt'
        if not os.path.exists(out_path):
            print(f'Downloading to {out_path}...')
            urllib.request.urlretrieve(mappings_url, out_path)
            print(f'Downloaded {os.path.getsize(out_path)} bytes')
        else:
            print(f'Already exists: {os.path.getsize(out_path)} bytes')
        break
