import json

with open(r'build\versions-1.20.1-common\classes\java\main\versions-1.20.1-common-refmap.json') as f:
    d = json.load(f)

for cls in ['dev/msf/friends/mixin/ClientLoginMixin', 'dev/msf/friends/mixin/ServerLoginMixin', 'dev/msf/friends/mixin/OnlineOptionsScreenMixin']:
    m = d.get('mappings', {}).get(cls, {})
    di = d.get('data', {}).get('named:intermediary', {}).get(cls, {})
    print(f'=== {cls} ===')
    print(f'  mappings: {json.dumps(m, indent=4)}')
    print(f'  data: {json.dumps(di, indent=4)}')
    print()
