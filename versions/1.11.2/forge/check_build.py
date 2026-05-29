import zipfile, struct, os

# Check the newly built jar
build_libs = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\build\libs'
for f in os.listdir(build_libs):
    fp = os.path.join(build_libs, f)
    print(f"{f}: {os.path.getsize(fp)} bytes")

# Check the reobfuscated jar (this is what gets deployed)
for fname in os.listdir(build_libs):
    fp = os.path.join(build_libs, fname)
    if fname.endswith('.jar'):
        z = zipfile.ZipFile(fp)
        print(f"\n=== {fname} ===")
        
        for name in z.namelist():
            if name.endswith('.class'):
                data = z.read(name)
                magic = struct.unpack('>I', data[0:4])[0]
                major = struct.unpack('>H', data[6:8])[0]
                
                # Check constant pool for tag 0
                cp_count = struct.unpack('>H', data[8:10])[0]
                offset = 10
                has_error = False
                for i in range(1, cp_count):
                    if offset >= len(data):
                        has_error = True
                        break
                    tag = data[offset]
                    if tag == 0:
                        has_error = True
                        print(f"  *** TAG 0 at CP#{i} in {name}")
                        break
                    if tag == 1:
                        length = struct.unpack('>H', data[offset+1:offset+3])[0]
                        offset += 3 + length
                    elif tag in (7, 8, 16, 19, 20):
                        offset += 3
                    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                        offset += 5
                    elif tag in (5, 6):
                        offset += 9
                        i += 1
                    elif tag == 15:
                        offset += 4
                    else:
                        has_error = True
                        print(f"  *** Unknown tag {tag} at CP#{i} in {name}")
                        break
                
                status = "OK" if not has_error else "ERROR"
                print(f"  {name}: magic=0x{magic:08x} ver={major} cp={cp_count} [{status}]")
        z.close()
