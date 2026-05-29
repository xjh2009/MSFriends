#!/usr/bin/env python3
"""
Post-build patch for Forge 1.20.1: fix refmap entries in the fat jar.
Adds missing @Shadow field entries so mixin can find them in SRG-named runtime.
"""
import zipfile
import json
import os

jar_path = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\versions-1.20.1-forge\libs\versions-1.20.1-forge-0.1.0+26.1.2-all.jar'
refmap_name = 'versions-1.20.1-common-refmap.json'

with zipfile.ZipFile(jar_path, 'r') as zin:
    refmap_raw = zin.read(refmap_name).decode('utf-8')
    other_files = {name: zin.read(name) for name in zin.namelist() if name != refmap_name}

refmap = json.loads(refmap_raw)
mappings = refmap.get('mappings', {})

# OptionsMixin: @Shadow allKeys field (GameOptions.allKeys -> f_92059_)
if 'dev/msf/friends/mixin/OptionsMixin' not in mappings:
    mappings['dev/msf/friends/mixin/OptionsMixin'] = {
        'allKeys': 'f_92059_:[Lnet/minecraft/client/KeyMapping;'
    }
    print('Added OptionsMixin refmap entry')

# TitleScreenMixin: @Shadow width/height fields
if 'dev/msf/friends/mixin/TitleScreenMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/TitleScreenMixin']
    if 'width' not in entry:
        entry['width'] = 'f_96543_:I'
        entry['height'] = 'f_96544_:I'
        new_entry = {'width': entry['width'], 'height': entry['height']}
        for k, v in entry.items():
            if k not in ('width', 'height'):
                new_entry[k] = v
        mappings['dev/msf/friends/mixin/TitleScreenMixin'] = new_entry
        print('Added width/height to TitleScreenMixin refmap entry')

# ClientPacketListenerMixin: @Shadow getConnection method
# obf g -> SRG m_104910_ returns Connection (obf sd -> mojang net.minecraft.network.Connection)
if 'dev/msf/friends/mixin/ClientPacketListenerMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ClientPacketListenerMixin']
    if 'getConnection' not in entry:
        entry['getConnection'] = 'Lnet/minecraft/client/multiplayer/ClientPacketListener;m_104910_()Lnet/minecraft/network/Connection;'
        print('Added getConnection to ClientPacketListenerMixin refmap entry')

# ServerLoginMixin: @Shadow fields and method
if 'dev/msf/friends/mixin/ServerLoginMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ServerLoginMixin']
    if 'server' not in entry:
        entry['server'] = 'f_10018_:Lnet/minecraft/server/MinecraftServer;'
        print('Added server to ServerLoginMixin refmap entry')
    if 'connection' not in entry:
        entry['connection'] = 'f_10013_:Lnet/minecraft/network/Connection;'
        print('Added connection to ServerLoginMixin refmap entry')
    if 'profile' not in entry:
        entry['profile'] = 'f_10021_:Lcom/mojang/authlib/GameProfile;'
        print('Added profile to ServerLoginMixin refmap entry')
    if 'acceptPlayer' not in entry:
        entry['acceptPlayer'] = 'm_10055_()V'
        print('Added acceptPlayer to ServerLoginMixin refmap entry')

# ClientLoginMixin: @Shadow connection field
if 'dev/msf/friends/mixin/ClientLoginMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ClientLoginMixin']
    if 'connection' not in entry:
        entry['connection'] = 'f_104522_:Lnet/minecraft/network/Connection;'
        print('Added connection to ClientLoginMixin refmap entry')

# ShareToLanScreenMixin: @Shadow parent field
if 'dev/msf/friends/mixin/ShareToLanScreenMixin' in mappings:
    entry = mappings['dev/msf/friends/mixin/ShareToLanScreenMixin']
    if 'parent' not in entry:
        entry['parent'] = 'f_96643_:Lnet/minecraft/client/gui/screens/Screen;'
        print('Added parent to ShareToLanScreenMixin refmap entry')

# NOTE: GuiGraphics IS the correct Mojang name in 1.20.1 (not DrawContext which is Yarn).
# Do NOT replace GuiGraphics with DrawContext.

# Fix refmap KEYS: relocateFatJar patches bytecode from Yarn->Mojang names but
# the refmap keys remain as Yarn names. The mixin processor can't match them.
# We need to apply Yarn->Mojang class replacements to the keys as well.
import urllib.request
mojang_file = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\mojang-client-1.20.1.txt'
if not os.path.exists(mojang_file):
    os.makedirs(os.path.dirname(mojang_file), exist_ok=True)
    urllib.request.urlretrieve('https://piston-data.mojang.com/v1/objects/6c48521eed01fe2e8ecdadbd5ae348415f3c47da/client.txt', mojang_file)
yarn_file = os.path.join(os.path.expanduser('~'), r'.gradle\caches\fabric-loom\1.20.1\net.fabricmc.yarn.1_20_1.1.20.1+build.10\mappings-base.tiny')
# Build obf->yarn and obf->mojang maps
obf_to_yarn = {}
obf_to_mojang = {}
with open(yarn_file) as f:
    for line in f:
        parts = line.strip().split('\t')
        if len(parts) >= 4 and parts[0] == 'CLASS':
            obf_to_yarn[parts[1]] = parts[3]
with open(mojang_file) as f:
    for line in f:
        if ' -> ' in line and not line.startswith(' ') and not line.startswith('#'):
            parts = line.strip().split(' -> ')
            if len(parts) == 2:
                obf_to_mojang[parts[1].strip().rstrip(':')] = parts[0].strip().replace('.', '/')
# Build yarn->mojang replacements for classes that differ
yarn_to_mojang = {}
for obf, yarn_name in obf_to_yarn.items():
    mojang = obf_to_mojang.get(obf)
    if mojang and yarn_name != mojang:
        yarn_to_mojang[yarn_name] = mojang

def remap_key(s):
    """Replace Yarn class names with Mojang names in a refmap key."""
    result = s
    # Sort by length descending so longer names match first
    for yarn_name, mojang_name in sorted(yarn_to_mojang.items(), key=lambda x: -len(x[0])):
        if yarn_name in result:
            result = result.replace(yarn_name, mojang_name)
    return result

key_fixes = 0
for cls_name in list(mappings.keys()):
    entry = mappings[cls_name]
    new_entry = {}
    for key, value in entry.items():
        new_key = remap_key(key)
        if new_key != key:
            key_fixes += 1
            print(f'Key fix: {cls_name}: {key} -> {new_key}')
        new_entry[new_key] = value
    mappings[cls_name] = new_entry

# Also fix data section keys
data = refmap.get('data', {})
for data_key, data_section in data.items():
    if isinstance(data_section, dict):
        for cls_name in list(data_section.keys()):
            entry = data_section[cls_name]
            if isinstance(entry, dict):
                new_entry = {}
                for key, value in entry.items():
                    new_key = remap_key(key)
                    new_entry[new_key] = value
                data_section[cls_name] = new_entry

print(f'Fixed {key_fixes} refmap keys (Yarn->Mojang)')

refmap_bytes = json.dumps(refmap, indent=2).encode('utf-8')

tmp_path = jar_path + '.tmp'
with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
    zout.writestr(refmap_name, refmap_bytes)
    for name, data in other_files.items():
        zout.writestr(name, data)

os.replace(tmp_path, jar_path)
print(f'Patched refmap in {jar_path}')
