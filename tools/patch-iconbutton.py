"""Patch IconButtonWidget and other specific classes with known SRG names."""
import zipfile, struct, sys, shutil
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'

# Per-class patches
FIXES = {
    'dev/msf/friends/screen/IconButtonWidget.class': {
        'getMessage': 'm_6035_', 'EMPTY': 'f_131282_', 'alpha': 'f_93625_',
        'x': 'f_93620_', 'y': 'f_93621_', 'width': 'f_93618_', 'height': 'f_93619_',
        'setShaderColor': 'm_157429_', 'setShaderTexture': 'm_157456_',
        'disableBlend': 'm_69461_', 'enableBlend': 'm_69478_',
    },
    'dev/msf/friends/mixin/TitleScreenMixin.class': {
        'width': 'f_96543_', 'height': 'f_96544_',
        'getInstance': 'm_91087_', 'setScreen': 'm_91152_', 'getWindowTitle': 'm_91270_',
    },
    'dev/msf/friends/mixin/PauseScreenMixin.class': {
        'width': 'f_96543_', 'height': 'f_96544_',
        'getMessage': 'm_6035_', 'getInstance': 'm_91087_', 'setScreen': 'm_91152_', 'getString': 'm_50789_',
    },
    'dev/msf/friends/mixin/ShareToLanScreenMixin.class': {
        'width': 'f_96543_', 'height': 'f_96544_',
    },
    'dev/msf/friends/mixin/MinecraftTitleMixin.class': {
        'getWindowTitle': 'm_91270_', 'getInstance': 'm_91087_',
    },
    'dev/msf/friends/mixin/KeyBindingMixin.class': {
        'setScreen': 'm_91152_',
    },
    'dev/msf/friends/mixin/ServerLoginMixin.class': {
        'getSessionService': 'm_91108_',
    },
    'dev/msf/friends/screen/FriendsScreen.class': {
        'width': 'f_96543_', 'height': 'f_96544_',
        'getMessage': 'm_6035_', 'addDrawableChild': 'm_142416_',
        'setScreen': 'm_91152_', 'getString': 'm_50789_', 'EMPTY': 'f_131282_',
    },
    'dev/msf/friends/screen/FriendsScreen$BaseEntry.class': {
        'width': 'f_93618_', 'height': 'f_93619_', 'getMessage': 'm_6035_',
    },
    'dev/msf/friends/screen/FriendsScreen$FriendEntry.class': {
        'width': 'f_93618_', 'height': 'f_93619_', 'getMessage': 'm_6035_',
    },
    'dev/msf/friends/screen/FriendsScreen$IncomingEntry.class': {
        'width': 'f_93618_', 'height': 'f_93619_', 'getMessage': 'm_6035_',
    },
    'dev/msf/friends/screen/FriendsScreen$OutgoingEntry.class': {
        'width': 'f_93618_', 'height': 'f_93619_', 'getMessage': 'm_6035_',
    },
    'dev/msf/friends/screen/FriendsScreen$FriendsPlayerList.class': {
        'width': 'f_93618_', 'height': 'f_93619_',
    },
    'dev/msf/friends/screen/P2PConnectScreen.class': {
        'width': 'f_96543_', 'height': 'f_96544_',
        'getMessage': 'm_6035_', 'setScreen': 'm_91152_', 'getString': 'm_50789_', 'EMPTY': 'f_131282_',
    },
    'dev/msf/friends/screen/FriendToast.class': {
        'getMessage': 'm_6035_', 'EMPTY': 'f_131282_',
    },
}

with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}

shutil.copy2(JAR, JAR + '.bak_final')

total = 0
for cls_name, raw_data in entries.items():
    if not cls_name.endswith('.class'): continue
    patches_map = FIXES.get(cls_name)
    if not patches_map: continue
    
    data = bytearray(raw_data)
    pos = 8
    cc = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    utf8 = []
    i = 1; sp = pos
    while i < cc:
        tag = data[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', data[sp:sp+2])[0]
            utf8.append((i, sp, sp+2, l))
            sp += 2 + l
        elif tag in (3,4): sp += 4
        elif tag in (5,6): sp += 8; i += 1
        elif tag in (7,8): sp += 2
        elif tag in (9,10,11,18): sp += 4
        elif tag == 12: sp += 4
        elif tag == 15: sp += 3
        elif tag in (16,19,20): sp += 2
        else: break
        i += 1
    
    patches = []
    for cp_idx, lfp, dp, length in utf8:
        s = data[dp:dp+length].decode('utf-8', errors='replace')
        if s in patches_map:
            patches.append((lfp, dp, length, patches_map[s]))
    
    if not patches: continue
    
    patches.sort(key=lambda x: x[1], reverse=True)
    for lfp, dp, length, new_s in patches:
        nb = new_s.encode('utf-8')
        data[lfp] = (len(nb) >> 8) & 0xFF
        data[lfp+1] = len(nb) & 0xFF
        data[dp:dp+length] = nb
    
    entries[cls_name] = bytes(data)
    total += len(patches)
    print('  %s: %d patches' % (cls_name.split('/')[-1], len(patches)))

with zipfile.ZipFile(JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, d in entries.items():
        zout.writestr(name, d)

print('\nTotal: %d patches' % total)
