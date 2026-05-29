"""Verify fatJar integrity and remapping status"""
import zipfile, struct, sys
sys.stdout.reconfigure(encoding='utf-8')

jar = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\versions-1.15.2-forge\libs\versions-1.15.2-forge-0.1.0+26.1.2-all.jar'
with zipfile.ZipFile(jar) as z:
    classes = [n for n in z.namelist() if n.endswith('.class')]
    bad = 0
    for name in classes:
        data = z.read(name)
        if len(data) < 10 or struct.unpack('>I', data[0:4])[0] != 0xCAFEBABE:
            bad += 1; continue
        cp = struct.unpack('>H', data[8:10])[0]
        idx = 10; slot = 1
        while slot < cp and idx < len(data):
            tag = data[idx]
            if tag == 1:
                if idx+3 > len(data): bad += 1; break
                idx += 3 + struct.unpack('>H', data[idx+1:idx+3])[0]; slot += 1
            elif tag in (7,8,16,19,20): idx += 3; slot += 1
            elif tag in (3,4,9,10,11,12,17,18): idx += 5; slot += 1
            elif tag in (5,6): idx += 9; slot += 2
            elif tag == 15: idx += 4; slot += 1
            else: bad += 1; break
    print("Total: %d, Bad: %d" % (len(classes), bad))

    d = z.read('dev/msf/friends/mixin/TitleScreenMixin.class')
    print("TitleScreenMixin MainMenuScreen: %s" % (b'MainMenuScreen' in d))
    print("TitleScreenMixin TitleScreen: %s" % (b'TitleScreen' in d))
    d2 = z.read('dev/msf/friends/mixin/OptionsMixin.class')
    print("OptionsMixin keysAll: %s" % (b'keysAll' in d2))
    print("OptionsMixin field_74324_K: %s" % (b'field_74324_K' in d2))
