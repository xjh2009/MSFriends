import zipfile
import sys

jar = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\versions-1.19.2-forge\libs\versions-1.19.2-forge-0.1.0+26.1.2-all.jar'

with zipfile.ZipFile(jar) as zf:
    # Check Logging.class
    data = zf.read('dev/msf/friends/util/Logging.class')
    for target in [b'get', b'm_125964']:
        positions = []
        idx = 0
        while True:
            idx = data.find(target, idx)
            if idx < 0: break
            positions.append(idx)
            idx += 1
        print(f'Logging.class - {target.decode()}: {len(positions)} occurrences')
        for p in positions:
            ctx = data[max(0,p-10):p+len(target)+20]
            print(f'  {p}: {ctx}')
    
    # Check MsfFriendsBoot.class
    data2 = zf.read('dev/msf/friends/MsfFriendsBoot.class')
    for target in [b'Logging.get', b'Logging.logger']:
        idx = data2.find(target)
        found = 'FOUND' if idx >= 0 else 'NOT found'
        print(f'MsfFriendsBoot - {target.decode()}: {found} (offset {idx})')
    
    # List all class files
    classes = [n for n in zf.namelist() if n.endswith('.class')]
    print(f'\nTotal class files: {len(classes)}')
    
    # Check for any remaining 'Logging.get' across all classes
    count = 0
    for name in classes:
        d = zf.read(name)
        if b'Logging.get' in d or b'Logging\x00\x03get' in d:
            print(f'  WARNING: {name} still references Logging.get')
            count += 1
    if count == 0:
        print('No files reference Logging.get - CLEAN!')
