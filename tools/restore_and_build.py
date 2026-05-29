import shutil, subprocess, sys, os

root = r"c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

# 1. Restore settings.gradle.kts from settings-forge1152
src_settings = os.path.join(root, "settings-forge1152.gradle.kts")
dst_settings = os.path.join(root, "settings.gradle.kts")
shutil.copy2(src_settings, dst_settings)
print(f"Settings restored from {src_settings}")
with open(dst_settings, 'r') as f:
    content = f.read()
    print(f"  Settings: {len(content)} chars, has forge: {'1.15.2:forge' in content}")

# 2. Restore build.gradle.kts for 1.15.2 forge from .bak
bak = os.path.join(root, "versions", "1.15.2", "forge", "build.gradle.kts.bak")
dst = os.path.join(root, "versions", "1.15.2", "forge", "build.gradle.kts")
# Read .bak with cp1252 encoding and fix smart quotes
with open(bak, 'rb') as f:
    raw = f.read()
# Replace cp1252 smart quotes with ASCII equivalents
replacements = {
    b'\x93': b'"', b'\x94': b'"',  # left/right double quotes
    b'\x91': b"'", b'\x92': b"'",  # left/right single quotes
    b'\x96': b'-', b'\x97': b'-',  # en/em dashes
    b'\x85': b'...',               # ellipsis
}
for old, new in replacements.items():
    raw = raw.replace(old, new)
text = raw.decode('cp1252', errors='replace')
with open(dst, 'w', encoding='utf-8') as f:
    f.write(text)
print(f"Build.gradle.kts restored: {len(text)} chars")
with open(dst, 'r') as f:
    c = f.read()
    print(f"  Has forge.gradle: {'forge.gradle' in c}")
    print(f"  Has fatJar: {'fatJar' in c}")

print("\nFiles restored. Ready to build.")