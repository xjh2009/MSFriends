"""Add -XDstringConcat=inline for Java 8 runtime modules."""
import os

path = os.path.join(r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi', 'build.gradle.kts')
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old = '        options.encoding = "UTF-8"'
new = (
    '        // Disable invokedynamic string concat for Java 8 runtime (1.15.2/1.14.4/1.13.2)\n'
    '        if (project.path.contains("1.15.2") || project.path.contains("1.14.4") || project.path.contains("1.13.2")) {\n'
    '            options.compilerArgs.add("-XDstringConcat=inline")\n'
    '        }\n'
    '        options.encoding = "UTF-8"'
)

if old in content:
    content = content.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Added -XDstringConcat=inline')
else:
    print('Target not found')
