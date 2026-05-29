import shutil
src = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.15.2\forge\build.gradle.kts.bak'
dst = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.15.2\forge\build.gradle.kts'

data = open(src, 'rb').read()
print(f'Source length: {len(data)} bytes')

# Try decoding as cp1252 (Windows Western European)
text = data.decode('cp1252')
# Replace smart quotes with regular quotes
text = text.replace('\u201c', '"').replace('\u201d', '"')
text = text.replace('\u2018', "'").replace('\u2019', "'")
# Also handle raw 0x93/0x94 bytes that are Windows smart quotes in cp1252
text = text.replace('\x93', '"').replace('\x94', '"')
text = text.replace('\x91', "'").replace('\x92', "'")

open(dst, 'w', encoding='utf-8', newline='\n').write(text)
d2 = open(dst, 'r', encoding='utf-8').read()
print(f'Written {len(d2)} chars to build.gradle.kts')
print(f'Contains forge.gradle: {"forge.gradle" in d2}')
print(f'Contains fatJar: {"fatJar" in d2}')
