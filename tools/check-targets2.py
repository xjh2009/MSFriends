import re, os, glob

base = r'versions\1.20.1\common\src\main\java'
for f in glob.glob(os.path.join(base, 'dev/msf/friends/mixin/**/*.java'), recursive=True):
    name = os.path.basename(f).replace('.java', '')
    with open(f, encoding='utf-8') as fh:
        content = fh.read()
    match = re.search(r'@Mixin\s*\(\s*([\w.]+\.class)', content)
    if match:
        target = match.group(1)
        print(f'{name}: @Mixin({target})')
    else:
        match2 = re.search(r'@Mixin\s*\(\s*\{([^}]+)\}', content)
        if match2:
            print(f'{name}: @Mixin({match2.group(1).strip()})')
        else:
            print(f'{name}: no @Mixin found')
