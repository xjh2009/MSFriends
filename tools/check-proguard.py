import re

with open(r'build\mojang-client-1.20.1.txt') as f:
    content = f.read()

# Find ClientHandshakePacketListenerImpl and ServerLoginPacketListenerImpl
targets = ['ClientHandshakePacketListenerImpl', 'ServerLoginPacketListenerImpl', 'SimpleOptionsSubScreen', 'OptionsSubScreen', 'Screen']
lines = content.split('\n')

current_class = None
for line in lines:
    if ' -> ' in line and not line.startswith(' '):
        for t in targets:
            if t in line:
                current_class = line.strip()
                print(f'\n{current_class}')
                break
        else:
            current_class = None
    elif current_class and line.startswith(' '):
        print(f'  {line.strip()}')
