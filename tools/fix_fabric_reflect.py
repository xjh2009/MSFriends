"""Fix FabricReflect.java to be Java 8 compatible."""
import re

path = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.15.2\common\src\main\java\dev\msf\friends\bridge\FabricReflect.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace Map.ofEntries with static init helper
content = content.replace(
    'private static final Map<String, String> CLASS_MAP = java.util.Map.ofEntries(',
    'private static final Map<String, String> CLASS_MAP = initClassMap();\n    private static Map<String, String> initClassMap() {\n        Map<String, String> m = new java.util.HashMap<>();'
)
content = content.replace(
    'private static final Map<String, String> METHOD_MAP = java.util.Map.ofEntries(',
    'private static final Map<String, String> METHOD_MAP = initMethodMap();\n    private static Map<String, String> initMethodMap() {\n        Map<String, String> m = new java.util.HashMap<>();'
)
content = content.replace(
    'private static final Map<String, String> FIELD_MAP = java.util.Map.ofEntries(',
    'private static final Map<String, String> FIELD_MAP = initFieldMap();\n    private static Map<String, String> initFieldMap() {\n        Map<String, String> m = new java.util.HashMap<>();'
)

# Replace SimpleEntry entries with m.put()
content = re.sub(
    r'new java\.util\.AbstractMap\.SimpleEntry<>\("([^"]*)",\s*"([^"]*)"\)',
    r'm.put("\1", "\2")',
    content
)

# Close methods - replace trailing ); with return m; }
content = re.sub(r'm\.put\(([^)]*)\);\n\s*\);', r'm.put(\1);\n        return m;\n    }', content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed to Java 8 compatible HashMap')
