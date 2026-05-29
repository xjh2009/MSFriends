"""Thorough constant pool dump for MsfFriendsBoot1112.class"""
import zipfile, struct, sys

jar = 'build/libs/msfriends-forge-1.11.2-0.1.0.jar'
name = 'dev/msf/friends/MsfFriendsBoot1112.class'

with zipfile.ZipFile(jar) as z:
    data = z.read(name)

magic, minor, major = struct.unpack_from('>IHH', data, 0)
cp_count = struct.unpack_from('>H', data, 8)[0]
print("magic=0x%08x version=%d.%d cp_count=%d size=%d" % (magic, major, minor, cp_count, len(data)))

pos = 10
idx = 1
long_skip_next = False
while idx < cp_count:
    if pos >= len(data):
        print("TRUNCATED at CP#%d offset=%d" % (idx, pos))
        break
    tag = data[pos]
    if tag == 0:
        print("*** TAG 0 at CP#%d offset=%d ***" % (idx, pos))
        # dump surrounding 60 bytes
        start = max(0, pos - 60)
        end = min(len(data), pos + 60)
        for off in range(start, end, 16):
            hex_part = ' '.join('%02x' % data[i] for i in range(off, min(end, off+16)))
            ascii_part = ''.join(chr(data[i]) if 32 <= data[i] < 127 else '.' for i in range(off, min(end, off+16)))
            marker = ' <-- HERE' if off <= pos < off+16 else ''
            print("  %04x: %-48s %s%s" % (off, hex_part, ascii_part, marker))
        break
    if tag == 1:  # Utf8
        length = struct.unpack_from('>H', data, pos+1)[0]
        raw = data[pos+3:pos+3+length]
        try:
            s = raw.decode('utf-8')
        except:
            s = repr(raw)
        print("  CP#%3d @%04d tag=1 Utf8 len=%d: %s" % (idx, pos, length, s[:100]))
        pos += 3 + length
    elif tag == 3:
        val = struct.unpack_from('>i', data, pos+1)[0]
        print("  CP#%3d @%04d tag=3 Integer=%d" % (idx, pos, val))
        pos += 5
    elif tag == 4:
        val = struct.unpack_from('>f', data, pos+1)[0]
        print("  CP#%3d @%04d tag=4 Float=%f" % (idx, pos, val))
        pos += 5
    elif tag == 5:
        val = struct.unpack_from('>q', data, pos+1)[0]
        print("  CP#%3d @%04d tag=5 Long=%d" % (idx, pos, val))
        pos += 9
        idx += 1  # Long takes 2 slots
    elif tag == 6:
        val = struct.unpack_from('>d', data, pos+1)[0]
        print("  CP#%3d @%04d tag=6 Double=%f" % (idx, pos, val))
        pos += 9
        idx += 1  # Double takes 2 slots
    elif tag == 7:
        ref = struct.unpack_from('>H', data, pos+1)[0]
        print("  CP#%3d @%04d tag=7 Class=#%d" % (idx, pos, ref))
        pos += 3
    elif tag == 8:
        ref = struct.unpack_from('>H', data, pos+1)[0]
        print("  CP#%3d @%04d tag=8 String=#%d" % (idx, pos, ref))
        pos += 3
    elif tag == 9:
        ci, nt = struct.unpack_from('>HH', data, pos+1)
        print("  CP#%3d @%04d tag=9 Fieldref=#%d.#%d" % (idx, pos, ci, nt))
        pos += 5
    elif tag == 10:
        ci, nt = struct.unpack_from('>HH', data, pos+1)
        print("  CP#%3d @%04d tag=10 Methodref=#%d.#%d" % (idx, pos, ci, nt))
        pos += 5
    elif tag == 11:
        ci, nt = struct.unpack_from('>HH', data, pos+1)
        print("  CP#%3d @%04d tag=11 InterfaceMethodref=#%d.#%d" % (idx, pos, ci, nt))
        pos += 5
    elif tag == 12:
        ni, ti = struct.unpack_from('>HH', data, pos+1)
        print("  CP#%3d @%04d tag=12 NameAndType=#%d:#%d" % (idx, pos, ni, ti))
        pos += 5
    elif tag == 15:
        kind = data[pos+1]
        ref = struct.unpack_from('>H', data, pos+2)[0]
        print("  CP#%3d @%04d tag=15 MethodHandle kind=%d ref=#%d" % (idx, pos, kind, ref))
        pos += 4
    elif tag == 16:
        ref = struct.unpack_from('>H', data, pos+1)[0]
        print("  CP#%3d @%04d tag=16 MethodType=#%d" % (idx, pos, ref))
        pos += 3
    elif tag == 18:
        bsm, nat = struct.unpack_from('>HH', data, pos+1)
        print("  CP#%3d @%04d tag=18 InvokeDynamic bsm=#%d nat=#%d" % (idx, pos, bsm, nat))
        pos += 5
    else:
        print("  CP#%3d @%04d tag=%d UNKNOWN" % (idx, pos, tag))
        break
    idx += 1

print("\nAfter CP parsing, pos=%d, next bytes:" % pos)
for off in range(pos, min(len(data), pos+80), 16):
    hex_part = ' '.join('%02x' % data[i] for i in range(off, min(len(data), off+16)))
    print("  %04x: %s" % (off, hex_part))
