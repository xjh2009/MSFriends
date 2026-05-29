import zipfile
jar = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.14.4-28.2.30\snapshot\20190601-1.14.2\recompiled.jar'
# Search for remaining missing classes
search = [
    'GuiSlot', 'GuiTextField', 'GuiYesNo', 'GuiYesNoCallback',
    'GuiButton', 'Gui', 'GuiChat', 'GuiPlayerTabOverlay',
    'CPacketLoginStart', 'SPacketEncryptionRequest', 'SPacketJoinGame',
    'NetHandlerLoginClient', 'NetHandlerPlayClient', 'NetHandlerLoginServer',
    'WorldClient', 'TextComponentTranslation', 'GuiButtonExt',
    'ExtendedList', 'TextFieldWidget', 'ConfirmScreen', 'Button',
    'ITooltip', 'AbstractButton', 'Widget'
]
with zipfile.ZipFile(jar) as z:
    all_classes = [n for n in z.namelist() if n.endswith('.class')]
    for s in search:
        found = [n for n in all_classes if n.split('/')[-1] == s + '.class']
        if found:
            for f in found:
                fqn = f.replace('/', '.').replace('.class', '')
                print(f'{s} -> {fqn}')
        else:
            # Try partial match
            partial = [n for n in all_classes if s.lower() in n.split('/')[-1].lower() and '$' not in n.split('/')[-1]]
            if partial:
                print(f'{s} -> NOT EXACT, partial matches:')
                for p in partial[:5]:
                    print(f"    {p.replace('/', '.').replace('.class', '')}")
            else:
                print(f'{s} -> NOT FOUND')
