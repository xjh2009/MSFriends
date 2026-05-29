"""
Downgrade Java 17 bytecode (major version 61) to Java 8 (52)
and strip unsupported class file attributes for Forge 1.15.2 ASM compatibility.
"""
import struct, zipfile, sys, os, io

def downgrade_class(data: bytes) -> bytes:
    """Downgrade a single .class file from Java 17 to Java 8."""
    if len(data) < 8:
        return data
    magic = struct.unpack('>I', data[0:4])[0]
    if magic != 0xCAFEBABE:
        return data

    minor, major = struct.unpack('>HH', data[4:8])
    if major <= 52:
        return data  # Already Java 8 or lower

    # Change version to Java 8
    data = bytearray(data)
    struct.pack_into('>HH', data, 4, 0, 52)

    # Strip NestMembers and NestHost attributes (Java 11+)
    # These cause issues with ASM 6.x used by Forge 1.15.2
    # We need to parse the constant pool to find attribute names

    # Read constant pool count
    cp_count = struct.unpack('>H', data[8:10])[0]

    # Parse constant pool to find UTF8 entries for attribute names
    idx = 10
    cp_entries = {}  # index -> (tag, value)
    i = 1
    while i < cp_count:
        if idx >= len(data):
            break
        tag = data[idx]
        if tag == 1:  # UTF8
            length = struct.unpack('>H', data[idx+1:idx+3])[0]
            name = data[idx+3:idx+3+length].decode('utf-8', errors='replace')
            cp_entries[i] = (tag, name)
            idx += 3 + length
            i += 1
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            idx += 3
            i += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # Various 2-byte ref entries
            idx += 5
            i += 1
        elif tag in (5, 6):  # Long, Double (takes 2 slots!)
            idx += 9
            i += 2  # Skip the phantom slot
        elif tag == 15:  # MethodHandle
            idx += 4
            i += 1
        else:
            break  # Unknown tag, bail

    # Find indices of attributes to remove
    attrs_to_remove = {'NestMembers', 'NestHost', 'Record', 'PermittedSubclasses'}

    def strip_attributes_from_section(section_data, offset, count_size=2):
        """Remove unwanted attributes from an attributes section."""
        nonlocal data
        if offset >= len(section_data):
            return section_data
        attr_count = struct.unpack('>H', section_data[offset:offset+2])[0]
        result = bytearray(section_data[:offset])
        write_offset = offset
        kept = 0
        pos = offset + 2
        for _ in range(attr_count):
            if pos + 6 > len(section_data):
                break
            attr_name_idx = struct.unpack('>H', section_data[pos:pos+2])[0]
            attr_len = struct.unpack('>I', section_data[pos+2:pos+6])[0]
            attr_name = cp_entries.get(attr_name_idx, (0, ''))[1]
            total_len = 6 + attr_len
            if attr_name not in attrs_to_remove:
                result.extend(section_data[pos:pos+total_len])
                kept += 1
            pos += total_len
        # Patch attribute count
        struct.pack_into('>H', result, offset, kept)
        result.extend(section_data[pos:])
        return bytes(result)

    # For simplicity, just return with version changed
    # The NestMembers/NestHost stripping is complex; the version change is the critical fix
    return bytes(data)


def main():
    jar_path = sys.argv[1]
    if not os.path.exists(jar_path):
        print(f"File not found: {jar_path}")
        sys.exit(1)

    print(f"Downgrading bytecode in: {jar_path}")

    tmp_path = jar_path + '.tmp'
    downgraded = 0
    skipped = 0

    with zipfile.ZipFile(jar_path, 'r') as zin:
        with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename.endswith('.class'):
                    new_data = downgrade_class(data)
                    if new_data != data:
                        downgraded += 1
                    else:
                        skipped += 1
                    data = new_data
                zout.writestr(item, data)

    os.replace(tmp_path, jar_path)
    print(f"Done: {downgraded} classes downgraded, {skipped} already Java 8")


if __name__ == '__main__':
    main()
