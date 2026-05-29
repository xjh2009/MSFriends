"""Build fatJar for Forge 1.14.4 and downgrade class files for ASM 6.2 compatibility."""
import zipfile, os, struct

PROJ = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi'
FORGE_JAR = os.path.join(PROJ, r'build\versions-1.14.4-forge\libs\versions-1.14.4-forge-0.1.0+26.1.2.jar')
COMMON_JAR = os.path.join(PROJ, r'build\common\libs\common-0.1.0+26.1.2.jar')
# SLF4J API - needed at runtime, not provided by Forge 1.14.4
SLF4J_JAR = r'C:\Users\xjh37\.gradle\caches\modules-2\files-2.1\org.slf4j\slf4j-api\1.7.36\6c62681a2f655b49963a5983b8b0950a6120ae14\slf4j-api-1.7.36.jar'
OUT_JAR = os.path.join(PROJ, r'build\versions-1.14.4-forge\libs\versions-1.14.4-forge-0.1.0+26.1.2-all.jar')
TARGET_MAJOR = 52  # Java 8

RECORD_UTF8 = b'\x00\x10java/lang/Record'
OBJECT_UTF8 = b'\x00\x10java/lang/Object'

# Attribute name UTF8 values in the constant pool (2-byte length prefix + name)
NEST_HOST_ATTR = b'\x00\x08NestHost'
NEST_MEMBERS_ATTR = b'\x00\x0aNestMembers'
RECORD_ATTR = b'\x00\x06Record'
PERMITTED_ATTR = b'\x00\x14PermittedSubclasses'


def patch_class(data):
    """Downgrade class file version and strip incompatible attributes."""
    if len(data) < 10 or data[:4] != b'\xca\xfe\xba\xbe':
        return data
    major = (data[6] << 8) | data[7]
    if major <= TARGET_MAJOR:
        return data

    data = bytearray(data)

    # Step 1: Downgrade major version
    data[6] = 0
    data[7] = TARGET_MAJOR

    # Step 2: Fix Record superclass in constant pool
    search = 10
    while True:
        pos = data.find(RECORD_UTF8, search)
        if pos == -1:
            break
        data[pos:pos+18] = OBJECT_UTF8
        search = pos + 16

    # Step 3: Rename incompatible attribute names in constant pool
    # ASM 6.2 throws UnsupportedOperationException for NestMembers/NestHost
    # By renaming them, ASM calls visitUnknown() which is a no-op
    # Same-length replacements to avoid corrupting the constant pool
    renames = [
        (b'NestMembers', b'NestMemberz'),      # 11 -> 11
        (b'NestHost',     b'NestHosx'),         # 8 -> 8
        (b'PermittedSubclasses', b'PermittedSubclassez'),  # 20 -> 20 (actually 19 -> 19)
        (b'Record',       b'Xecord'),           # 6 -> 6
    ]
    for old, new in renames:
        assert len(old) == len(new), f"Length mismatch: {old} vs {new}"
        search = 10
        while True:
            pos = data.find(old, search)
            if pos == -1:
                break
            data[pos:pos+len(new)] = new
            search = pos + len(new)

    return bytes(data)


def build_fatjar():
    entries = {}

    print(f'Adding forge jar: {os.path.basename(FORGE_JAR)}')
    with zipfile.ZipFile(FORGE_JAR) as z:
        for name in z.namelist():
            if name.startswith('META-INF/MANIFEST.MF'):
                continue
            entries[name] = z.read(name)

    print(f'Adding common jar: {os.path.basename(COMMON_JAR)}')
    with zipfile.ZipFile(COMMON_JAR) as z:
        for name in z.namelist():
            if name.startswith('META-INF/MANIFEST.MF'):
                continue
            if name in entries:
                continue
            entries[name] = z.read(name)

    # Add SLF4J API (required at runtime by the mod, not provided by Forge 1.14.4)
    if os.path.exists(SLF4J_JAR):
        print(f'Adding SLF4J: {os.path.basename(SLF4J_JAR)}')
        with zipfile.ZipFile(SLF4J_JAR) as z:
            for name in z.namelist():
                if name.startswith('META-INF/MANIFEST.MF') or name == 'module-info.class':
                    continue
                if name in entries:
                    continue
                entries[name] = z.read(name)
    else:
        print(f'WARNING: SLF4J jar not found: {SLF4J_JAR}')

    print(f'Writing fatJar: {os.path.basename(OUT_JAR)}')
    patched = 0
    with zipfile.ZipFile(OUT_JAR, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            if name.endswith('.class'):
                old_major = (data[6] << 8) | data[7] if len(data) > 7 else 0
                data = patch_class(data)
                if old_major > TARGET_MAJOR:
                    patched += 1
            z.writestr(name, data)

    size_mb = os.path.getsize(OUT_JAR) / (1024 * 1024)
    print(f'Patched {patched} class files')
    print(f'Created fatJar: {size_mb:.2f} MB')

    # Verify
    max_major = 0
    with zipfile.ZipFile(OUT_JAR) as z:
        for name in z.namelist():
            if name.endswith('.class'):
                data = z.read(name)
                if len(data) > 7:
                    major = (data[6] << 8) | data[7]
                    if major > max_major:
                        max_major = major
    print(f'Max class version: {max_major} (target: {TARGET_MAJOR})')

if __name__ == '__main__':
    build_fatjar()
