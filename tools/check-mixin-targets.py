import json, re, os

# Read mixin config
with open(r'versions\1.20.1\common\src\main\resources\msf-friends.mixins.json') as f:
    config = json.load(f)
package = config.get('package', '')
print(f'Package: {package}')

base = r'versions\1.20.1\common\src\main\java'
for mixin in config.get('mixins', []):
    path = os.path.join(base, package.replace('.', '/'), mixin.replace('.', '/') + '.java')
    if os.path.exists(path):
        with open(path) as f:
            content = f.read()
        # Find @Mixin annotation
        match = re.search(r'@Mixin\s*\(\s*(?:value\s*=\s*)?(?:\{)?\s*([A-Za-z.]+\.class)', content)
        if match:
            target = match.group(1).replace('.class', '')
            print(f'{mixin} -> @Mixin({target}.class)')
        else:
            print(f'{mixin} -> ??? (could not parse @Mixin)')
    else:
        print(f'{mixin} -> file not found')
