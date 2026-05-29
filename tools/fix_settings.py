#!/usr/bin/env python3
"""DEPRECATED - DO NOT RUN. Use rebuild_settings.py instead."""
import sys
print('This script is deprecated. Use rebuild_settings.py.')
sys.exit(0)

# Build the full settings file
additions = """
include(":versions:1.14.4:forge")
include(":versions:1.13.2:forge")
"""

new_content = base + '\n' + additions + '\n'

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(new_content)

print(f'Written {len(new_content)} bytes')
# Verify
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
print(f'Total lines: {len(lines)}')
for line in lines:
    if '1.14' in line or '1.13' in line:
        print(f'  {line.rstrip()}')
