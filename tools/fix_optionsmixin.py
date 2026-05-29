#!/usr/bin/env python3
"""Apply OptionsMixin refmap fix to build.gradle.kts"""

bak_path = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts.bak'
target = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.19.2\forge\build.gradle.kts'

with open(bak_path, encoding='utf-8') as f:
    content = f.read()

old1 = (
    '            val convertedText = refmapText.replace(Regex("\\"([^\\"]+)\\")") { match ->\n'
    '                val str = match.groupValues[1]\n'
    '                // Only transform strings that look like descriptors (contain ; and class references)\n'
    '                if (str.contains(";") && (str.contains("class_") || str.contains("field_") || str.contains("method_"))) {\n'
    '                    "\\"${transformRefmapValue(str)}\\""\n'
    '                } else {\n'
    '                    match.value  // Keep keys and non-descriptor values unchanged\n'
    '                }\n'
    '            }\n'
    '            \n'
    '            refmapFile.writeText(convertedText)\n'
    '            logger.lifecycle("Converted Fabric refmap to Forge format (Yarn\u2192SRG+Mojang)")\n'
    '        } else {'
)

new1 = (
    '            var convertedText = refmapText.replace(Regex("\\"([^\\"]+)\\")") { match ->\n'
    '                val str = match.groupValues[1]\n'
    '                // Only transform strings that look like descriptors (contain ; and class references)\n'
    '                if (str.contains(";") && (str.contains("class_") || str.contains("field_") || str.contains("method_"))) {\n'
    '                    "\\"${transformRefmapValue(str)}\\""\n'
    '                } else {\n'
    '                    match.value  // Keep keys and non-descriptor values unchanged\n'
    '                }\n'
    '            }\n'
    '            \n'
    '            // Second pass: also convert Yarn class names in refmap KEYS.\n'
    '            // The Forge mixin system remaps descriptor classes before looking up the refmap,\n'
    '            // so keys must use Mojang class names, not Yarn names.\n'
    '            for ((yarnRef, mojangRef) in pathQualified) {\n'
    '                if (yarnRef.startsWith("L") && mojangRef.startsWith("L")) {\n'
    '                    convertedText = convertedText.replace(yarnRef, mojangRef)\n'
    '                }\n'
    '            }\n'
    '            \n'
    '            refmapFile.writeText(convertedText)\n'
    '            logger.lifecycle("Converted Fabric refmap to Forge format (Yarn\u2192SRG+Mojang)")\n'
    '            \n'
    '            // Add missing refmap entries for mixin @Shadow fields the AP didn\'t generate.\n'
    '            var refmapText2 = refmapFile.readText()\n'
    '            if (!refmapText2.contains("OptionsMixin")) {\n'
    '                refmapText2 = refmapText2.replace(\n'
    '                    "\\"mappings\\": {",\n'
    '                    "\\"mappings\\": {\\n" +\n'
    '                    "    \\"dev/msf/friends/mixin/OptionsMixin\\": {\\"allKeys\\": \\"f_92059_:[Lnet/minecraft/client/KeyMapping;\\"},",\n'
    '                )\n'
    '                refmapFile.writeText(refmapText2)\n'
    '                logger.lifecycle("Added missing refmap entry for OptionsMixin (allKeys \u2192 f_92059_)")\n'
    '            }\n'
    '        } else {'
)

if old1 not in content:
    print('ERROR: old text not found in backup')
    idx = content.find('refmapFile.writeText(convertedText)')
    if idx >= 0:
        print(f'Found at offset {idx}')
        print(repr(content[idx-200:idx+200]))
    else:
        print('Not found at all')
else:
    content = content.replace(old1, new1, 1)
    with open(target, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Written {len(content)} chars with both fixes')
    content = content.replace(old, new, 1)
    with open(target, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Written {len(content)} chars with OptionsMixin fix')
