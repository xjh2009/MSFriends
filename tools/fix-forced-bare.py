"""
Force-replace all known Yarn bare names used by MSF Friends mod classes.
These are ambiguous names that can't be resolved by the simple unambiguous remapper.
"""
import zipfile, struct, sys, shutil
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'

# All known Yarn->SRG replacements for names used in MSF Friends mod
FORCED = {
    # Screen (eaq) fields
    'width': 'f_96543_',
    'height': 'f_96544_',
    # AbstractWidget (dwy) methods
    'getMessage': 'm_6035_',     # dwy.g()Los;
    # Screen (eaq) methods
    'addDrawableChild': 'm_142416_',  # eaq.d(Ldxy;)Ldxy;
    'addDrawable': 'm_142414_',       # eaq
    'addSelectableChild': 'm_96623_', # eaq
    'clearChildren': 'm_169380_',     # eaq
    'initWidgets': 'm_7861_',         # ead (PauseScreen)
    'isPauseScreen': 'm_7859_',       # eaq
    # Minecraft (class_310/dvt) methods
    'setScreen': 'm_91152_',          # dvt.setScreen
    'getInstance': 'm_91087_',        # dvt.getInstance
    'getServer': 'm_91190_',          # dvt.getServer
    'getSessionService': 'm_91275_',  # dvt.getSessionService
    'getWindowTitle': 'm_91386_',     # dvt.getWindowTitle
    # Component (class_2561/os) methods
    'getString': 'm_50789_',          # os.getString
    # Font (class_327/dwq) methods
    'getStringWidth': 'm_92901_',     # dwq.width(String)
    # RenderSystem methods - ALL use SRG names at Forge runtime
    'setShaderColor': 'm_157429_',     # (FFFF)V
    'setShaderTexture': 'm_157456_',   # (ILww;)V - ResourceLocation
    'disableBlend': 'm_69461_',
    'enableBlend': 'm_69478_',
    # GuiComponent (class_339/eaz) methods
    'drawTexture': 'm_93208_',        # eaz.blit
    # AbstractContainerScreen (class_464/ean) methods
    'centerX': 'm_96594_',
    # Button (class_339/dxa) methods
    'setWidth': 'm_93666_',
    # LiteralText (class_2562/pf) fields
    'EMPTY': 'f_131282_',
    # ButtonWidget/AbstractWidget (dwy) fields
    'active': 'f_93623_',             # dwy.active
    'visible': 'f_93624_',            # dwy.visible
    'message': 'f_93622_',            # dwy.message
    'alpha': 'f_93625_',              # dwy.alpha
    # Identifier (class_2960/abb) fields
    'NAMESPACE': 'f_135784_',         # abb.NAMESPACE_SEPARATOR
    # GameType (class_1934/dcg) fields
    'SURVIVAL': 'f_6077_',
    'CREATIVE': 'f_6078_',
    'ADVENTURE': 'f_6079_',
    'SPECTATOR': 'f_6080_',
    # KeyMapping (class_304/dvm) methods
    'isDown': 'm_90864_',
    'setDown': 'm_90837_',
    'consumeClick': 'm_90863_',
    'getKey': 'm_90868_',
}

# Read jar
with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}

shutil.copy2(JAR, JAR + '.bak5')

total_patches = 0
patched_classes = 0

for cls_name, raw_data in entries.items():
    if not cls_name.endswith('.class') or 'dev/msf/friends/' not in cls_name:
        continue
    
    data = bytearray(raw_data)
    pos = 8
    cp_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    
    utf8_entries = []
    i = 1; sp = pos
    while i < cp_count:
        tag = data[sp]; sp += 1
        if tag == 1:
            l = struct.unpack('>H', data[sp:sp+2])[0]
            utf8_entries.append((i, sp, sp+2, l))
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
    for cp_idx, lfp, dp, length in utf8_entries:
        s = data[dp:dp+length].decode('utf-8', errors='replace')
        if s in FORCED and FORCED[s] != s:
            patches.append((lfp, dp, length, FORCED[s]))
    
    if not patches:
        continue
    
    patches.sort(key=lambda x: x[1], reverse=True)
    for lfp, dp, length, new_s in patches:
        nb = new_s.encode('utf-8')
        data[lfp] = (len(nb) >> 8) & 0xFF
        data[lfp+1] = len(nb) & 0xFF
        data[dp:dp+length] = nb
    
    entries[cls_name] = bytes(data)
    total_patches += len(patches)
    patched_classes += 1
    print("  %s: %d patches" % (cls_name, len(patches)))

with zipfile.ZipFile(JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    for name, d in entries.items():
        zout.writestr(name, d)

print("\nPatched %d classes, %d total patches" % (patched_classes, total_patches))
