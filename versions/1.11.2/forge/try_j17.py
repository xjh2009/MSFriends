import subprocess, os

# Compile just MsfFriendsBoot1112 with Java 17 --release 8 and check the result
src_dir = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\src\main\java'
out_dir = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.11.2\forge\test-j17'
os.makedirs(out_dir, exist_ok=True)

# Classpath from ForgeGradle
cp_parts = [
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraftforge\forge\1.11.2-13.20.1.2588\forge-1.11.2-13.20.1.2588.jar',
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar',
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\com\google\guava\guava\21.0\guava-21.0.jar',
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\org\apache\logging\log4j\log4j-api\2.8.1\log4j-api-2.8.1.jar',
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\libraries\org\apache\logging\log4j\log4j-core\2.8.1\log4j-core-2.8.1.jar',
    r'C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.11.2\1.11.2.jar',
]
cp = ';'.join(cp_parts)

# Find all java files
import glob
srcs = glob.glob(os.path.join(src_dir, '**/*.java'), recursive=True)
print(f"Found {len(srcs)} source files")

# Write to a file
with open(os.path.join(out_dir, 'sources.txt'), 'w') as f:
    for s in srcs:
        f.write(s + '\n')

# Compile with Java 17 --release 8
j17 = r'C:\Program Files\Zulu\zulu-17\bin\javac.exe'
cmd = [j17, '--release', '8', '-cp', cp, '-encoding', 'UTF-8', '-d', out_dir, '@' + os.path.join(out_dir, 'sources.txt')]
print(f"Running: {' '.join(cmd[:6])} ...")
result = subprocess.run(cmd, capture_output=True, text=True)
print(f"Exit code: {result.returncode}")
if result.stdout: print(f"STDOUT: {result.stdout[:500]}")
if result.stderr: print(f"STDERR: {result.stderr[:500]}")

# Check the output
import struct
boot_class = os.path.join(out_dir, 'dev', 'msf', 'friends', 'MsfFriendsBoot1112.class')
if os.path.exists(boot_class):
    with open(boot_class, 'rb') as f:
        data = f.read()
    
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
            print(f"*** TAG 0 at CP#{i}, offset={offset}")
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
            print(f"*** Unknown tag {tag} at CP#{i}")
            break
    
    status = "OK" if not has_error else "ERROR"
    print(f"MsfFriendsBoot1112.class: size={len(data)}, cp={cp_count}, [{status}]")
