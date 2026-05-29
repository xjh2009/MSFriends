"""
Test if ASM 5.0.3 can parse MsfFriendsForge.class.
Simulates what BlamingTransformer does.
"""
import zipfile, struct, sys

# ASM ClassReader format check
# The ClassReader constructor checks:
# 1. Magic = 0xCAFEBABE
# 2. Constant pool entries are valid
# 3. Major version is reasonable

jar_path = sys.argv[1] if len(sys.argv) > 1 else 'build/libs/msfriends-forge-1.11.2-0.1.0.jar'

# First, extract MsfFriendsForge.class
with zipfile.ZipFile(jar_path) as z:
    for name in sorted(z.namelist()):
        if not name.endswith('.class'):
            continue
        data = z.read(name)
        magic = struct.unpack_from('>I', data, 0)[0]
        minor, major = struct.unpack_from('>HH', data, 4)
        if magic != 0xCAFEBABE:
            print("BAD MAGIC: %s" % name)
            continue
        
        # Try to parse entire constant pool
        cp_count = struct.unpack_from('>H', data, 8)[0]
        pos = 10
        ok = True
        for i in range(1, cp_count):
            if pos >= len(data):
                print("TRUNCATED CP at #%d: %s" % (i, name))
                ok = False
                break
            tag = data[pos]
            sizes = {1: None, 3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 
                     9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 18: 5}
            if tag == 0:
                print("TAG 0 at CP#%d: %s" % (i, name))
                ok = False
                break
            if tag not in sizes:
                print("UNKNOWN TAG %d at CP#%d: %s" % (tag, i, name))
                ok = False
                break
            if tag == 1:
                if pos + 3 > len(data):
                    print("TRUNCATED UTF8: %s" % name)
                    ok = False
                    break
                length = struct.unpack_from('>H', data, pos+1)[0]
                pos += 3 + length
                if pos > len(data):
                    print("TRUNCATED UTF8 data: %s" % name)
                    ok = False
                    break
            else:
                pos += sizes[tag]
            if tag in (5, 6):
                i += 1  # skip next slot
                
        if not ok:
            continue
            
        # Now parse after CP - access_flags, this_class, super_class, interfaces, fields, methods, attributes
        if pos + 8 > len(data):
            print("TRUNCATED after CP: %s" % name)
            continue
            
        access_flags = struct.unpack_from('>H', data, pos)[0]
        this_class = struct.unpack_from('>H', data, pos+2)[0]
        super_class = struct.unpack_from('>H', data, pos+4)[0]
        iface_count = struct.unpack_from('>H', data, pos+6)[0]
        pos += 8 + iface_count * 2
        
        print("OK: %s (v%d.%d cp=%d this=#%d super=#%d ifaces=%d)" % (
            name, major, minor, cp_count, this_class, super_class, iface_count))

print("\nDone.")
