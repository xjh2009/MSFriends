import os, re

common_dir = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\common\src\main\java'
features = {
    'switch expression': r'return\s+switch|=\s*switch\s*\(',
    'instanceof pattern': r'instanceof\s+\w+\s+\w+',
    'record ': r'\brecord\s+\w+\s*\(',
    'sealed ': r'\bsealed\s+(class|interface)',
    'text block': r'"""',
    'var ': r'\bvar\s+\w+\s*=',
}

for root, dirs, files in os.walk(common_dir):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        fpath = os.path.join(root, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f, 1):
                for feature, pattern in features.items():
                    if re.search(pattern, line):
                        rel = os.path.relpath(fpath, common_dir)
                        print(f'{rel}:{i}: [{feature}] {line.rstrip()}')
