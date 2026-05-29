#!/usr/bin/env python3
"""Fix safety check in build.gradle.kts.bak to allow dev/msf/ class references"""
import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'versions', '1.19.2', 'forge', 'build.gradle.kts.bak')
with open(path, encoding='utf-8') as f:
    content = f.read()

old = (
    'if (className !in mcClassNames) {\n'
    '                                allRefsAreMC = false\n'
    '                                break\n'
    '                            }'
)
new = (
    'if (!className.startsWith("dev/msf/") && className !in mcClassNames) {\n'
    '                                allRefsAreMC = false\n'
    '                                break\n'
    '                            }'
)

if old in content:
    content = content.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Fixed safety check to allow dev/msf/ classes')
else:
    print('Old text not found')
