"""Analyze constant pool and BootstrapMethods of a class file."""
import zipfile, struct, sys

JAR = r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.17.1-forge-37.1.1\mods\msf-friends.jar'
TARGET = sys.argv[1] if len(sys.argv) > 1 else 'dev/msf/friends/mixin/TitleScreenMixin.class'

z = zipfile.ZipFile(JAR)
data = z.read(TARGET)

# Parse constant pool
pos = 10
cp_count = struct.unpack('>H', data[8:10])[0]

entries = {}
i = 1
sp = pos
while i < cp_count:
    tag = data[sp]; sp += 1
    if tag == 1:
        l = struct.unpack('>H', data[sp:sp+2])[0]
        entries[i] = (1, data[sp+2:sp+2+l].decode('utf-8', errors='replace'))
        sp += 2 + l
    elif tag == 3: entries[i] = (3, None); sp += 4
    elif tag == 4: entries[i] = (4, None); sp += 4
    elif tag == 5: entries[i] = (5, None); sp += 8; i += 1
    elif tag == 6: entries[i] = (6, None); sp += 8; i += 1
    elif tag == 7:
        entries[i] = (7, struct.unpack('>H', data[sp:sp+2])[0]); sp += 2
    elif tag == 8:
        entries[i] = (8, struct.unpack('>H', data[sp:sp+2])[0]); sp += 2
    elif tag == 9:
        c, n = struct.unpack('>HH', data[sp:sp+4])
        entries[i] = (9, c, n); sp += 4
    elif tag == 10:
        c, n = struct.unpack('>HH', data[sp:sp+4])
        entries[i] = (10, c, n); sp += 4
    elif tag == 11:
        c, n = struct.unpack('>HH', data[sp:sp+4])
        entries[i] = (11, c, n); sp += 4
    elif tag == 12:
        n, d = struct.unpack('>HH', data[sp:sp+4])
        entries[i] = (12, n, d); sp += 4
    elif tag == 15:
        entries[i] = (15, data[sp], struct.unpack('>H', data[sp+1:sp+3])[0]); sp += 3
    elif tag == 16:
        entries[i] = (16, struct.unpack('>H', data[sp:sp+2])[0]); sp += 2
    elif tag == 18:
        b, n = struct.unpack('>HH', data[sp:sp+4])
        entries[i] = (18, b, n); sp += 4
    elif tag == 19: sp += 2
    elif tag == 20: sp += 2
    else: break
    i += 1

def resolve(idx):
    e = entries.get(idx)
    if e is None: return f'[?{idx}]'
    if e[0] == 1: return e[1]
    if e[0] == 7:
        return resolve(e[1])
    if e[0] == 12:
        return resolve(e[1]) + ' : ' + resolve(e[2])
    if e[0] in (9, 10, 11):
        kind = {9: 'F', 10: 'M', 11: 'IM'}[e[0]]
        return f'{resolve(e[1])}.{resolve(e[2])} [{kind}]'
    if e[0] == 15:
        kinds = {1: 'GETFIELD', 2: 'GETSTATIC', 3: 'PUTFIELD', 4: 'PUTSTATIC',
                 5: 'INVOKEVIRTUAL', 6: 'INVOKESTATIC', 7: 'INVOKESPECIAL',
                 8: 'NEWINVOKESPECIAL', 9: 'INVOKEINTERFACE'}
        return f'MH({kinds.get(e[1], "?")}, {resolve(e[2])})'
    return f'[tag{e[0]}:{e[1:]}]'

print(f'=== {TARGET} ===')
print(f'Constant pool count: {cp_count}')
print()

print('=== Methodrefs/Fieldrefs/InterfaceMethodrefs ===')
for idx, e in sorted(entries.items()):
    if e[0] in (9, 10, 11):
        print(f'  [{idx}] {resolve(idx)}')

print()
print('=== NameAndType entries ===')
for idx, e in sorted(entries.items()):
    if e[0] == 12:
        name = resolve(e[1])
        desc = resolve(e[2])
        print(f'  [{idx}] {name} {desc}')

print()
# Find BootstrapMethods attribute
sp2 = sp
acc = struct.unpack('>H', data[sp2:sp2+2])[0]
this_class = struct.unpack('>H', data[sp2+2:sp2+4])[0]
sp2 += 2
sp2 += 2  # super
ifc_cnt = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
sp2 += ifc_cnt * 2

# Skip fields
fc = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
for fi in range(fc):
    sp2 += 6
    ac = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
    for ai in range(ac):
        al = struct.unpack('>I', data[sp2+2:sp2+6])[0]; sp2 += 6 + al

# Skip methods
mc = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
methods = []
for mi in range(mc):
    m_acc = struct.unpack('>H', data[sp2:sp2+2])[0]
    m_name = struct.unpack('>H', data[sp2+2:sp2+4])[0]
    m_desc = struct.unpack('>H', data[sp2+4:sp2+6])[0]
    methods.append((m_acc, m_name, m_desc))
    sp2 += 6
    ac = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
    for ai in range(ac):
        al = struct.unpack('>I', data[sp2+2:sp2+6])[0]; sp2 += 6 + al

print(f'Methods ({mc}):')
for m_acc, m_name, m_desc in methods:
    print(f'  {resolve(m_name)} {resolve(m_desc)}')

# Class attributes
cac = struct.unpack('>H', data[sp2:sp2+2])[0]; sp2 += 2
for ai in range(cac):
    attr_name_idx = struct.unpack('>H', data[sp2:sp2+2])[0]
    attr_len = struct.unpack('>I', data[sp2+2:sp2+6])[0]
    attr_name = resolve(attr_name_idx)
    if attr_name == 'BootstrapMethods':
        bsm_count = struct.unpack('>H', data[sp2+6:sp2+8])[0]
        print(f'\nBootstrapMethods ({bsm_count}):')
        bp = sp2 + 8
        for bi in range(bsm_count):
            bsm_ref = struct.unpack('>H', data[bp:bp+2])[0]
            num_args = struct.unpack('>H', data[bp+2:bp+4])[0]
            bp += 4
            args = []
            for ai2 in range(num_args):
                args.append(struct.unpack('>H', data[bp:bp+2])[0])
                bp += 2
            print(f'  BSM[{bi}]: {resolve(bsm_ref)}')
            for ai2, arg_idx in enumerate(args):
                print(f'    arg[{ai2}] = [{arg_idx}] {resolve(arg_idx)}')
    elif attr_name == 'InnerClasses':
        ic_count = struct.unpack('>H', data[sp2+6:sp2+8])[0]
        print(f'\nInnerClasses ({ic_count}):')
        ip = sp2 + 8
        for ii in range(ic_count):
            inner = struct.unpack('>H', data[ip:ip+2])[0]
            outer = struct.unpack('>H', data[ip+2:ip+4])[0]
            inner_name = struct.unpack('>H', data[ip+4:ip+6])[0]
            inner_flags = struct.unpack('>H', data[ip+6:ip+8])[0]
            ip += 8
            print(f'  inner={resolve(inner)} outer={resolve(outer)} name={resolve(inner_name)}')
    sp2 += 6 + attr_len
