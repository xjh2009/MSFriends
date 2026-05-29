import zipfile, struct, sys
jar = 'build/libs/msfriends-forge-1.11.2-0.1.0.jar'
name = 'dev/msf/friends/MsfFriendsBoot1112.class'
with zipfile.ZipFile(jar) as z:
    data = z.read(name)
cp_count = struct.unpack_from('>H', data, 8)[0]
print('CP count: %d, file size: %d' % (cp_count, len(data)))
pos = 10
for i in range(1, cp_count):
    off = pos
    tag = data[pos]
    if tag == 0:
        print('  CP#%d offset=%d tag=0 ZERO/ERROR' % (i, off))
        # dump surrounding bytes
        start = max(0, off - 40)
        end = min(len(data), off + 40)
        hex_str = ' '.join('%02x' % b for b in data[start:end])
        print('  Surrounding bytes: %s' % hex_str)
        break
    if tag == 1:
        length = struct.unpack_from('>H', data, pos+1)[0]
        s = data[pos+3:pos+3+length].decode('utf-8', errors='replace')
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=1 Utf8(%d): %s' % (i, off, length, s[:80]))
        pos += 3 + length
    elif tag == 3:
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=3 Integer' % (i, off))
        pos += 5
    elif tag == 4:
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=4 Float' % (i, off))
        pos += 5
    elif tag == 5:
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=5 Long' % (i, off))
        pos += 9
    elif tag == 6:
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=6 Double' % (i, off))
        pos += 9
    elif tag == 7:
        idx = struct.unpack_from('>H', data, pos+1)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=7 Class -> #%d' % (i, off, idx))
        pos += 3
    elif tag == 8:
        idx = struct.unpack_from('>H', data, pos+1)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=8 String -> #%d' % (i, off, idx))
        pos += 3
    elif tag in (9, 10, 11):
        ci = struct.unpack_from('>H', data, pos+1)[0]
        nt = struct.unpack_from('>H', data, pos+3)[0]
        tn = {9:'Fieldref', 10:'Methodref', 11:'InterfaceMethodref'}[tag]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=%d %s #%d.#%d' % (i, off, tag, tn, ci, nt))
        pos += 5
    elif tag == 12:
        ni = struct.unpack_from('>H', data, pos+1)[0]
        ti = struct.unpack_from('>H', data, pos+3)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=12 NameAndType #%d:#%d' % (i, off, ni, ti))
        pos += 5
    elif tag == 15:
        ref_kind = data[pos+1]
        ref_idx = struct.unpack_from('>H', data, pos+2)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=15 MethodHandle kind=%d -> #%d' % (i, off, ref_kind, ref_idx))
        pos += 4
    elif tag == 16:
        idx = struct.unpack_from('>H', data, pos+1)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=16 MethodType -> #%d' % (i, off, idx))
        pos += 3
    elif tag == 18:
        bsi = struct.unpack_from('>H', data, pos+1)[0]
        bnat = struct.unpack_from('>H', data, pos+3)[0]
        if i >= cp_count - 25:
            print('  CP#%d offset=%d tag=18 InvokeDynamic bsm=#%d nat=#%d' % (i, off, bsi, bnat))
        pos += 5
    else:
        print('  CP#%d offset=%d tag=%d UNKNOWN' % (i, off, tag))
        break
