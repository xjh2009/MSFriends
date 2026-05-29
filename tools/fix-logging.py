import os, re

src_dir = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.14.4\forge\src\main\java'
count = 0
for root, dirs, files in os.walk(src_dir):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        fpath = os.path.join(root, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
        new_content = content.replace('Logging.logger()', 'Logging.get()')
        if new_content != content:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            rel = os.path.relpath(fpath, src_dir)
            print(f'Fixed: {rel}')
            count += 1
print(f'Total: {count}')
