#!/usr/bin/env python3
"""
Downgrade class file versions in a JAR from 61 (Java 17) to 55 (Java 11).
Also patches Record superclasses from java/lang/Record -> java/lang/Object,
so that Records compiled with Java 16+ can run on Java 11 as plain final classes.
"""
import sys, zipfile, os, tempfile, shutil

TARGET_MAJOR = 55  # Java 11
RECORD_UTF8 = b'\x00\x10java/lang/Record'   # CONSTANT_Utf8 length=16
OBJECT_UTF8 = b'\x00\x10java/lang/Object'    # CONSTANT_Utf8 length=16

def patch_jar(jar_path):
    tmp = tempfile.mkdtemp(prefix='clsdowngrade_')
    try:
        with zipfile.ZipFile(jar_path, 'r') as zin:
            zin.extractall(tmp)
        
        patched = 0
        records_fixed = 0
        for root, dirs, files in os.walk(tmp):
            for f in files:
                if f.endswith('.class'):
                    fp = os.path.join(root, f)
                    with open(fp, 'rb') as fh:
                        data = bytearray(fh.read())
                    if len(data) < 8 or data[0:4] != b'\xca\xfe\xba\xbe':
                        continue
                    major = data[6] * 256 + data[7]
                    if major <= TARGET_MAJOR:
                        continue
                    # Downgrade major version
                    data[6] = 0
                    data[7] = TARGET_MAJOR
                    # Patch Record superclass references in constant pool
                    # Replace "java/lang/Record" UTF8 entries with "java/lang/Object"
                    # "Record" is 17 bytes, "Object" is 16 bytes
                    # We set length to 16 and overwrite the first 16 bytes of the string
                    # The 17th byte (leftover 'd' from 'Record') is harmless
                    search = 10  # skip header (magic=4, minor=2, major=2, cp_count=2)
                    count = 0
                    while True:
                        pos = data.find(RECORD_UTF8, search)
                        if pos == -1:
                            break
                        data[pos:pos+18] = OBJECT_UTF8  # 2 bytes length + 16 bytes string, same size
                        records_fixed += 1
                        count += 1
                        search = pos + 16
                    with open(fp, 'wb') as fh:
                        fh.write(data)
                    patched += 1
        
        os.remove(jar_path)
        with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for root, dirs, files in os.walk(tmp):
                for f in files:
                    fp = os.path.join(root, f)
                    arcname = os.path.relpath(fp, tmp)
                    zout.write(fp, arcname)
        
        size_mb = os.path.getsize(jar_path) / (1024*1024)
        print(f"Patched {patched} .class files (major 61 -> {TARGET_MAJOR})")
        print(f"Fixed {records_fixed} Record -> Object superclass references")
        print(f"Output: {jar_path} ({size_mb:.2f} MB)")
    finally:
        shutil.rmtree(tmp)

if __name__ == '__main__':
    patch_jar(sys.argv[1])
