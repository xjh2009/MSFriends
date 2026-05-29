"""
Fix wrong SRG names that were incorrectly mapped by the Gradle build
(which used the wrong ProGuard file).
Also handles bytecode version downgrade.
"""
import zipfile, struct, sys, shutil
sys.stdout.reconfigure(encoding='utf-8')

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'

# Map of wrong SRG -> correct SRG (or Yarn -> correct SRG)
# These come from the Gradle build using wrong ProGuard
WRONG_TO_CORRECT = {
    # getWindowTitle was mapped to m_91386_ (wrong) -> m_91270_ (correct)
    'm_91386_': 'm_91270_',
    # setScreen was mapped to m_91152_ -> check if correct
    # getInstance was mapped to m_91087_ -> check if correct
    # getServer was mapped to m_91190_ -> check if correct
    # getSessionService was mapped to m_91275_ -> check if correct
}

# Also fix any remaining Yarn bare names with correct SRG
YARN_TO_SRG = {
    # Screen (eaq) fields
    'width': 'f_96543_',
    'height': 'f_96544_',
    # AbstractWidget (dwy) fields
    'alpha': 'f_93625_',
    'active': 'f_93623_',
    'visible': 'f_93624_',
    'message': 'f_93622_',
    # LiteralText (pf) fields
    'EMPTY': 'f_131282_',
    # Screen methods
    'addDrawableChild': 'm_142416_',
    'addDrawable': 'm_142414_',
    'addSelectableChild': 'm_96623_',
    'clearChildren': 'm_169380_',
    'initWidgets': 'm_7861_',
    'isPauseScreen': 'm_7859_',
    # AbstractWidget methods
    'getMessage': 'm_6035_',
    # Minecraft methods - verified against 1.17.1 TSRG (class dvp)
    'setScreen': 'm_91152_',  # dvp.a(eaq) - SRG verified
    'getInstance': 'm_91087_',  # dvp.C() - SRG verified
    'getServer': 'm_91092_',  # dvp.H() -> faq - SRG verified
    'getSessionService': 'm_91108_',  # dvp.Y() -> MinecraftSessionService - SRG verified
    'getWindowTitle': 'm_91270_',  # dvp.aH() - SRG verified
    # Font methods
    'getStringWidth': 'm_92901_',  # dwq.a(String)
    # Component methods
    'getString': 'm_50789_',  # os.getString()
    # GuiComponent methods
    'drawTexture': 'm_93208_',  # eaz.blit
    # Button methods
    'setWidth': 'm_93666_',  # dxa.setWidth
    # KeyMapping methods
    'isDown': 'm_90864_',
    'setDown': 'm_90837_',
    'consumeClick': 'm_90863_',
    'getKey': 'm_90868_',
    # RenderSystem - NOT obfuscated, SRG names differ from obf
    'setShaderColor': 'm_157429_',
    'setShaderTexture': 'm_157456_',
    'disableBlend': 'm_69461_',
    'enableBlend': 'm_69478_',
}

# Only patch dev/msf/friends/ classes
shutil.copy2(JAR, JAR + '.bak6')

with zipfile.ZipFile(JAR, 'r') as zin:
    entries = {name: zin.read(name) for name in zin.namelist()}

total_patches = 0
patched_classes = 0

for cls_name, raw_data in entries.items():
    if not cls_name.endswith('.class') or 'dev/msf/friends/' not in cls_name:
        continue
    
    data = bytearray(raw_data)
    
    # Downgrade bytecode
    if len(data) >= 8:
        major = struct.unpack('>H', data[6:8])[0]
        if major > 61:
            data[6] = 0; data[7] = 61
    
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
        # Check wrong SRG -> correct SRG
        if s in WRONG_TO_CORRECT:
            patches.append((lfp, dp, length, WRONG_TO_CORRECT[s]))
        # Check Yarn -> SRG
        elif s in YARN_TO_SRG:
            patches.append((lfp, dp, length, YARN_TO_SRG[s]))
    
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
