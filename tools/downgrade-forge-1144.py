#!/usr/bin/env python3
"""
Downgrade Java class files for Forge 1.14.4 ASM 6.2 compatibility.

ASM 6.2 does not support NestMembers/NestHost attributes (Java 11) and
will throw UnsupportedOperationException when scanning mod jars.

This script:
1. Downgrades the class file major version to 52 (Java 8)
2. Strips NestMembers, NestHost, Record, PermittedSubclasses attributes
3. Patches Record superclasses from java/lang/Record -> java/lang/Object
"""
import sys, zipfile, os, tempfile, shutil, struct

TARGET_MAJOR = 52  # Java 8 - Forge 1.14.4 needs Java 8 bytecode
STRIP_ATTRS = {b'NestMembers', b'NestHost', b'Record', b'PermittedSubclasses', b'SourceFile', b'SourceDebugExtension'}
# Keep SourceFile for debugging? No, strip it to be safe.
# Actually SourceFile is fine for ASM 6.2. Only strip the Java 11+ ones.
STRIP_ATTRS = {b'NestMembers', b'NestHost', b'Record', b'PermittedSubclasses'}
RECORD_UTF8 = b'\x00\x10java/lang/Record'
OBJECT_UTF8 = b'\x00\x10java/lang/Object'


def read_u2(data, offset):
    return (data[offset] << 8) | data[offset + 1]

def read_u4(data, offset):
    return (data[offset] << 24) | (data[offset+1] << 16) | (data[offset+2] << 8) | data[offset+3]

def write_u2(data, offset, val):
    data[offset] = (val >> 8) & 0xFF
    data[offset + 1] = val & 0xFF


def strip_attributes(data):
    """Strip unsupported attributes from a Java class file. Returns modified data."""
    if len(data) < 10 or data[0:4] != b'\xca\xfe\xba\xbe':
        return data, False

    # Parse constant pool to find UTF8 indices for attribute names
    cp_count = read_u2(data, 8)
    
    # Build a map of constant pool index -> UTF8 string
    cp_utf8 = {}  # index -> bytes
    offset = 10  # after magic(4) + minor(2) + major(2) + cp_count(2)
    
    for i in range(1, cp_count):
        tag = data[offset]
        if tag == 1:  # CONSTANT_Utf8
            length = read_u2(data, offset + 1)
            string_bytes = data[offset+3:offset+3+length]
            cp_utf8[i] = string_bytes
            offset += 3 + length
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            offset += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # Integer, Float, Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
            offset += 5
        elif tag in (5, 6):  # Long, Double
            offset += 9
            i += 1  # takes 2 cp slots
        elif tag == 15:  # MethodHandle: tag(1) + ref_kind(1) + ref_index(2) = 4
            offset += 4
        else:
            # Unknown tag, bail
            return data, False
    
    # Find cp indices for our target attribute names
    strip_indices = set()
    for idx, utf8_bytes in cp_utf8.items():
        if utf8_bytes in STRIP_ATTRS:
            strip_indices.add(idx)
    
    if not strip_indices:
        return data, False
    
    # Now parse past the rest of the header to find class attributes
    # After constant pool: access_flags(2), this_class(2), super_class(2)
    # Then interfaces, fields, methods, and finally class attributes
    
    # Skip access_flags, this_class, super_class
    access_flags = read_u2(data, offset)
    offset += 2
    this_class = read_u2(data, offset)
    offset += 2
    super_class = read_u2(data, offset)
    offset += 2
    
    # Skip interfaces
    iface_count = read_u2(data, offset)
    offset += 2 + iface_count * 2
    
    # Skip fields
    def skip_members(data, offset):
        count = read_u2(data, offset)
        offset += 2
        for _ in range(count):
            # access_flags, name_index, descriptor_index
            offset += 6
            # attributes
            attr_count = read_u2(data, offset)
            offset += 2
            for _ in range(attr_count):
                # attribute_name_index, attribute_length
                attr_len = read_u4(data, offset + 2)
                offset += 6 + attr_len
        return offset
    
    offset = skip_members(data, offset)  # fields
    offset = skip_members(data, offset)  # methods
    
    # Now at class attributes
    attr_count = read_u2(data, offset)
    attr_count_offset = offset
    offset += 2
    
    # Parse class attributes and mark ones to strip
    new_data = bytearray(data)
    removed = 0
    # We'll build a list of (start, end) for each attribute
    attr_positions = []
    for _ in range(attr_count):
        start = offset
        name_idx = read_u2(data, offset)
        attr_len = read_u4(data, offset + 2)
        end = offset + 6 + attr_len
        attr_positions.append((start, end, name_idx in strip_indices))
        offset = end
    
    if not any(should_strip for _, _, should_strip in attr_positions):
        return data, False
    
    # Rebuild: copy everything before class attributes, then only non-stripped attributes
    result = bytearray(data[:attr_count_offset + 2])
    new_count = attr_count
    for start, end, should_strip in attr_positions:
        if should_strip:
            new_count -= 1
            removed += 1
        else:
            result.extend(data[start:end])
    
    # Fix attribute count
    result[attr_count_offset] = (new_count >> 8) & 0xFF
    result[attr_count_offset + 1] = new_count & 0xFF
    
    return bytes(result), removed > 0


def patch_jar(jar_path):
    tmp = tempfile.mkdtemp(prefix='clsdowngrade_')
    try:
        with zipfile.ZipFile(jar_path, 'r') as zin:
            zin.extractall(tmp)
        
        patched_version = 0
        patched_attrs = 0
        records_fixed = 0
        
        for root, dirs, files in os.walk(tmp):
            for f in files:
                if f.endswith('.class'):
                    fp = os.path.join(root, f)
                    with open(fp, 'rb') as fh:
                        data = bytearray(fh.read())
                    
                    if len(data) < 10 or data[0:4] != b'\xca\xfe\xba\xbe':
                        continue
                    
                    changed = False
                    major = data[6] * 256 + data[7]
                    
                    # Skip classes that are already Java 8 (e.g. SLF4J 1.7.x with major=49)
                    if major <= TARGET_MAJOR:
                        continue
                    
                    # Downgrade major version
                    if major > TARGET_MAJOR:
                        data[6] = 0
                        data[7] = TARGET_MAJOR
                        patched_version += 1
                        changed = True
                    
                    # Strip unsupported attributes
                    result, did_strip = strip_attributes(bytes(data))
                    if did_strip:
                        data = bytearray(result)
                        patched_attrs += 1
                        changed = True
                    
                    # Patch Record -> Object (must happen BEFORE binary rename)
                    search = 10
                    while True:
                        pos = bytes(data).find(RECORD_UTF8, search)
                        if pos == -1:
                            break
                        data[pos:pos+18] = OBJECT_UTF8
                        records_fixed += 1
                        changed = True
                        search = pos + 16
                    
                    # Binary fallback: rename NestMembers/NestHost in the raw bytes
                    # so ASM 6.2 never sees them, even if strip_attributes failed.
                    # NOTE: Only rename attribute names that are ONLY used as class
                    # attributes. Do NOT rename 'Record' or 'PermittedSubclasses'
                    # because those strings may appear in other contexts (field names,
                    # method names, annotations, etc.) and would corrupt the class.
                    for old_name, new_name in [
                        (b'NestMembers', b'NestMemberz'),
                        (b'NestHost',    b'NestHosx'),
                    ]:
                        search2 = 10
                        while True:
                            pos2 = data.find(old_name, search2)
                            if pos2 == -1:
                                break
                            data[pos2:pos2+len(new_name)] = new_name
                            changed = True
                            search2 = pos2 + len(new_name)
                    search = 10
                    while True:
                        pos = bytes(data).find(RECORD_UTF8, search)
                        if pos == -1:
                            break
                        data[pos:pos+18] = OBJECT_UTF8
                        records_fixed += 1
                        changed = True
                        search = pos + 16
                    
                    if changed:
                        with open(fp, 'wb') as fh:
                            fh.write(data)
        
        os.remove(jar_path)
        with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for root, dirs, files in os.walk(tmp):
                for f in files:
                    fp = os.path.join(root, f)
                    arcname = os.path.relpath(fp, tmp)
                    zout.write(fp, arcname)
        
        size_mb = os.path.getsize(jar_path) / (1024*1024)
        print(f"Downgraded {patched_version} .class files to major {TARGET_MAJOR}")
        print(f"Stripped unsupported attributes from {patched_attrs} .class files")
        print(f"Fixed {records_fixed} Record -> Object superclass references")
        print(f"Output: {jar_path} ({size_mb:.2f} MB)")
    finally:
        shutil.rmtree(tmp)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <jar-path>")
        sys.exit(1)
    patch_jar(sys.argv[1])
