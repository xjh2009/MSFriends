import zipfile, os

jar_path = r'build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'

with zipfile.ZipFile(jar_path) as z:
    for name in z.namelist():
        if 'ClientLoginMixin.class' in name:
            data = z.read(name)
            # Print first 100 bytes as hex
            for i in range(0, min(200, len(data)), 16):
                hex_str = ' '.join(f'{b:02x}' for b in data[i:i+16])
                ascii_str = ''.join(chr(b) if 32 <= b < 127 else '.' for b in data[i:i+16])
                print(f'{i:4d}: {hex_str:<48s} {ascii_str}')
            
            # Also search for string 'connection' in raw bytes
            needle = b'connection'
            idx = 0
            while True:
                idx = data.find(needle, idx)
                if idx < 0:
                    break
                ctx_start = max(0, idx-10)
                ctx_end = min(len(data), idx+20)
                ctx = data[ctx_start:ctx_end]
                print(f'\nFound "connection" at byte {idx}:')
                hex_str = ' '.join(f'{b:02x}' for b in ctx)
                print(f'  hex: {hex_str}')
                idx += 1
            break
