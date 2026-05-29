import zipfile, os

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'
print('Exists:', os.path.exists(jar_path))
print('Size:', os.path.getsize(jar_path))

with zipfile.ZipFile(jar_path) as z:
    for name in z.namelist():
        if 'ClientLoginMixin' in name:
            info = z.getinfo(name)
            print(f'{name}: {info.file_size} bytes')
            data = z.read(name)
            print(f'First 4 bytes: {data[0]:02x} {data[1]:02x} {data[2]:02x} {data[3]:02x}')
            
            pos = 10
            cp_count = (data[pos] << 8) | data[pos+1]
            pos += 2
            print(f'CP count: {cp_count}')
            i = 1
            while i < cp_count and pos < len(data):
                tag = data[pos]
                pos += 1
                if tag == 1:
                    length = (data[pos] << 8) | data[pos+1]
                    pos += 2
                    val = data[pos:pos+length].decode('utf-8', errors='replace')
                    if len(val) < 200:
                        print(f'  #{i} UTF8: "{val}"')
                    pos += length
                elif tag in (3, 4): pos += 4
                elif tag in (5, 6): pos += 8; i += 1
                elif tag == 7: pos += 2
                elif tag == 8: pos += 2
                elif tag in (9, 10, 11): pos += 4
                elif tag == 12: pos += 4
                elif tag == 15: pos += 3
                elif tag == 16: pos += 2
                elif tag in (17, 18): pos += 4
                elif tag in (19, 20): pos += 2
                else:
                    print(f'  Unknown tag {tag} at pos {pos-1}')
                    break
                i += 1
