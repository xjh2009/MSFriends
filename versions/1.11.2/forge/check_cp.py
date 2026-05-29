"""Correctly parse Java class constant pool, accounting for Long/Double taking 2 slots."""
import zipfile, struct, os, sys

def parse_cp(data):
    cp_count = struct.unpack_from('>H', data, 8)[0]
    pos = 10
    i = 1
    while i < cp_count:
        if pos >= len(data):
            return None, "truncated at entry %d" % i
        tag = data[pos]
        if tag == 0:
            return None, "TAG 0 at CP#%d offset=%d" % (i, pos)
        skip = 1  # how many CP indices this entry consumes
        if tag == 1:
            length = struct.unpack_from('>H', data, pos+1)[0]
            pos += 3 + length
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            skip = 2  # Long/Double take 2 slots
        elif tag in (7, 8):
            pos += 3
        elif tag in (9, 10, 11, 12):
            pos += 5
        elif tag == 15:
            pos += 4
        elif tag == 16:
            pos += 3
        elif tag == 18:
            pos += 5
        else:
            return None, "Unknown tag %d at CP#%d offset=%d" % (tag, i, pos)
        i += skip
    return cp_count, None

def check_jar(jar_path):
    errors = []
    with zipfile.ZipFile(jar_path) as z:
        for name in sorted(z.namelist()):
            if not name.endswith('.class'):
                continue
            data = z.read(name)
            if len(data) < 10:
                errors.append((name, "too small"))
                continue
            magic = struct.unpack_from('>I', data, 0)[0]
            if magic != 0xCAFEBABE:
                errors.append((name, "bad magic: 0x%08x" % magic))
                continue
            cp_count, err = parse_cp(data)
            if err:
                errors.append((name, err))
    return errors

if __name__ == '__main__':
    jar = sys.argv[1] if len(sys.argv) > 1 else 'build/libs/msfriends-forge-1.11.2-0.1.0.jar'
    errors = check_jar(jar)
    if errors:
        print("ERRORS in %s:" % jar)
        for name, err in errors:
            print("  %s: %s" % (name, err))
        sys.exit(1)
    else:
        print("ALL CLEAN in %s" % jar)
        sys.exit(0)
