"""Check for MethodHandle (15), MethodType (16), InvokeDynamic (18) entries in class files.
These are used by Java 8 lambdas but might confuse ASM 5.0.3."""
import zipfile, struct, sys

jar_path = sys.argv[1] if len(sys.argv) > 1 else 'build/libs/msfriends-forge-1.11.2-0.1.0.jar'

with zipfile.ZipFile(jar_path) as z:
    for name in sorted(z.namelist()):
        if not name.endswith('.class'):
            continue
        data = z.read(name)
        cp_count = struct.unpack_from('>H', data, 8)[0]
        pos = 10
        entries = {}
        has_indy = False
        i = 1
        while i < cp_count:
            tag = data[pos]
            if tag == 1:
                length = struct.unpack_from('>H', data, pos+1)[0]
                entries[i] = ('Utf8', data[pos+3:pos+3+length].decode('utf-8', errors='replace'))
                pos += 3 + length
            elif tag == 3:
                entries[i] = ('Integer',)
                pos += 5
            elif tag == 4:
                entries[i] = ('Float',)
                pos += 5
            elif tag == 5:
                entries[i] = ('Long',)
                pos += 9
                i += 1
                continue
            elif tag == 6:
                entries[i] = ('Double',)
                pos += 9
                i += 1
                continue
            elif tag == 7:
                entries[i] = ('Class', struct.unpack_from('>H', data, pos+1)[0])
                pos += 3
            elif tag == 8:
                entries[i] = ('String', struct.unpack_from('>H', data, pos+1)[0])
                pos += 3
            elif tag in (9, 10, 11):
                ci, nt = struct.unpack_from('>HH', data, pos+1)
                entries[i] = ({9:'Fieldref', 10:'Methodref', 11:'InterfaceMethodref'}[tag], ci, nt)
                pos += 5
            elif tag == 12:
                ni, ti = struct.unpack_from('>HH', data, pos+1)
                entries[i] = ('NameAndType', ni, ti)
                pos += 5
            elif tag == 15:
                kind, ref = data[pos+1], struct.unpack_from('>H', data, pos+2)[0]
                entries[i] = ('MethodHandle', kind, ref)
                has_indy = True
                pos += 4
            elif tag == 16:
                ref = struct.unpack_from('>H', data, pos+1)[0]
                entries[i] = ('MethodType', ref)
                has_indy = True
                pos += 3
            elif tag == 18:
                bsm, nat = struct.unpack_from('>HH', data, pos+1)
                entries[i] = ('InvokeDynamic', bsm, nat)
                has_indy = True
                pos += 5
            else:
                break
            i += 1
        
        # Print summary
        mh_count = sum(1 for v in entries.values() if v[0] == 'MethodHandle')
        mt_count = sum(1 for v in entries.values() if v[0] == 'MethodType')
        id_count = sum(1 for v in entries.values() if v[0] == 'InvokeDynamic')
        
        if has_indy:
            print("HAS INDY: %s  (MH=%d MT=%d ID=%d)" % (name, mh_count, mt_count, id_count))
            # Print details
            for idx in sorted(entries.keys()):
                v = entries[idx]
                if v[0] in ('MethodHandle', 'MethodType', 'InvokeDynamic'):
                    print("  CP#%d: %s" % (idx, v))
