import zipfile
jar = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.14.4-28.2.30\snapshot\20190601-1.14.2\recompiled.jar'
search = [
    'GuiScreen', 'GuiButton', 'GuiIngameMenu', 'GuiMainMenu', 'GuiSlot',
    'GuiShareToLan', 'GuiTextField', 'GuiYesNo', 'GuiYesNoCallback',
    'CPacketLoginStart', 'SPacketEncryptionRequest', 'SPacketJoinGame',
    'NetHandlerLoginClient', 'NetHandlerPlayClient', 'NetHandlerLoginServer',
    'WorldClient', 'TextComponentTranslation', 'Gui', 'GuiButtonExt',
    'GuiConnecting', 'GuiPlayerTabOverlay', 'GuiChat'
]
with zipfile.ZipFile(jar) as z:
    names = [n for n in z.namelist() if n.endswith('.class')]
    for s in search:
        found = [n for n in names if n.split('/')[-1] == s + '.class']
        if found:
            fqn = found[0].replace('/', '.').replace('.class', '')
            print(f'{s} -> {fqn}')
        else:
            print(f'{s} -> NOT FOUND')
