import zipfile, struct, shutil, sys, json
sys.stdout.reconfigure(encoding='utf-8')

jar_path = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
backup = jar_path + '.bak'
shutil.copy2(jar_path, backup)

# Comprehensive Yarn→SRG bare name mapping for 1.17.1
# These are names that appear in the class constant pool as bare names
# and need to be remapped to their SRG equivalents
bare_name_fixes = {
    # Screen methods (eaq)
    'addDrawableChild': 'm_142416_',
    'addDrawable': 'm_142414_',
    'addSelectableChild': 'm_96623_',
    'clearChildren': 'm_169380_',
    'initWidgets': 'm_7861_',
    'setScreen': 'm_91152_',  # Minecraft.setScreen
    'getMessage': 'm_130765_',  # Component.getMessage
    'getString': 'm_50789_',  # Component.getString
    'getServer': 'm_91190_',  # Minecraft.getServer
    'getWindowTitle': 'm_91386_',  # Minecraft.getWindowTitle
    'getWidth': 'm_143436_',  # MinecraftScreen.width getter
    'setConnectedViaP2P': 'setConnectedViaP2P',  # our own method
    'getPresenceHandler': 'getPresenceHandler',  # our own
    'setMultiplayerScope': 'setMultiplayerScope',  # our own
    'tryUpdatePresence': 'tryUpdatePresence',  # our own
    'onHostScopeChanged': 'onHostScopeChanged',  # our own
    'onHostServerStopping': 'onHostServerStopping',  # our own
    'registerCategory': 'registerCategory',  # our own
    'getConnection': 'm_6198_',  # ClientPacketListener.getConnection
    'onGameJoin': 'm_6127_',  # ClientPacketListener.onGameJoin
    'getSessionService': 'm_91275_',  # Minecraft.getSessionService
    'fillProfileProperties': 'fillProfileProperties',  # authlib
    'getPlayerName': 'getPlayerName',  # authlib
    'getIntendedId': 'getIntendedId',  # authlib (not Yarn)
    'acceptPlayer': 'acceptPlayer',  # our own
    'onHello': 'm_7985_',  # ServerLoginPacketListenerImpl.onHello
    'onKey': 'm_7987_',  # ServerLoginPacketListenerImpl.onKey
    'getInstance': 'm_91087_',  # Minecraft.getInstance
    'onPress': 'onPress',  # Button.OnPress interface
    'getWindowTitle': 'm_91386_',
    'lanLabel': 'lanLabel',  # our own field
    'multiplayerScope': 'multiplayerScope',  # our own
    'onlineLabel': 'onlineLabel',  # our own
    'friendsBtn': 'friendsBtn',  # our own
    'lanBtn': 'lanBtn',  # our own
    'optionsBtn': 'optionsBtn',  # our own
    'reportBtn': 'reportBtn',  # our own
    'gameMode': 'gameMode',  # our own
    'centerX': 'm_96594_',  # GuiComponent.centerX (RenderHelper)
    'setWidth': 'setWidth',  # our own
    'getReturnValue': 'getReturnValue',  # our own
    'setReturnValue': 'setReturnValue',  # our own
    'fetchProfile': 'fetchProfile',  # our own
    'fillProfile': 'fillProfile',  # our own
    'fallbackName': 'fallbackName',  # our own
    'getSessionService': 'm_91275_',
    'intendedId': 'intendedId',  # our own
    'profileId': 'profileId',  # our own
    'profileMethod': 'profileMethod',  # our own
    'sessionService': 'sessionService',  # our own
    'isConnectedViaP2P': 'isConnectedViaP2P',  # our own
}

# Read all entries
with zipfile.ZipFile(jar_path, 'r') as zin:
    entries = {}
    for name in zin.namelist():
        entries[name] = zin.read(name)

# Patch all class files
patched = 0
total_patches = 0
for class_name, raw_data in entries.items():
    if not class_name.endswith('.class'):
        continue
    data = bytearray(raw_data)
    pos = 8
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    utf8_entries = []
    i = 1
    scan_pos = pos
    while i < cp_count:
        tag = data[scan_pos]; scan_pos += 1
        if tag == 1:
            length = struct.unpack('>H', data[scan_pos:scan_pos+2])[0]
            utf8_entries.append((i, scan_pos, scan_pos + 2, length))
            scan_pos += 2 + length
        elif tag in (3,4): scan_pos += 4
        elif tag in (5,6): scan_pos += 8; i += 1
        elif tag in (7,8): scan_pos += 2
        elif tag in (9,10,11,18): scan_pos += 4
        elif tag == 12: scan_pos += 4
        elif tag == 15: scan_pos += 3
        elif tag in (16,19,20): scan_pos += 2
        else: break
        i += 1

    patches = []
    for cp_idx, length_field_pos, data_pos, length in utf8_entries:
        s = data[data_pos:data_pos+length].decode('utf-8', errors='replace')
        if s in bare_name_fixes and bare_name_fixes[s] != s:
            new_s = bare_name_fixes[s]
            patches.append((length_field_pos, data_pos, length, new_s))

    if not patches:
        continue

    patches.sort(key=lambda x: x[1], reverse=True)
    for length_field_pos, data_pos, length, new_s in patches:
        new_bytes = new_s.encode('utf-8')
        new_len = len(new_bytes)
        data[length_field_pos] = (new_len >> 8) & 0xFF
        data[length_field_pos + 1] = new_len & 0xFF
        data[data_pos:data_pos+length] = new_bytes

    entries[class_name] = bytes(data)
    patched += 1
    total_patches += len(patches)

# Write new jar
with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, data in entries.items():
        zout.writestr(name, data)

print(f'Patched {patched} classes, {total_patches} total name replacements')
print(f'Backup: {backup}')
