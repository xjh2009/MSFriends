import os, re

src_dir = r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.14.4\forge\src\main\java'

# Mapping: old import -> new import
IMPORT_MAP = {
    'import net.minecraft.client.gui.GuiScreen;': 'import net.minecraft.client.gui.screen.Screen;',
    'import net.minecraft.client.gui.GuiButton;': 'import net.minecraft.client.gui.widget.button.Button;',
    'import net.minecraft.client.gui.GuiIngameMenu;': 'import net.minecraft.client.gui.screen.IngameMenuScreen;',
    'import net.minecraft.client.gui.GuiMainMenu;': 'import net.minecraft.client.gui.screen.MainMenuScreen;',
    'import net.minecraft.client.gui.GuiShareToLan;': 'import net.minecraft.client.gui.screen.ShareToLanScreen;',
    'import net.minecraft.client.gui.GuiYesNo;': 'import net.minecraft.client.gui.screen.ConfirmScreen;',
    'import net.minecraft.client.gui.GuiYesNoCallback;': 'import net.minecraft.client.gui.screen.ConfirmScreen;',
    'import net.minecraft.client.gui.GuiSlot;': 'import net.minecraft.client.gui.widget.list.ExtendedList;',
    'import net.minecraft.client.gui.GuiTextField;': 'import net.minecraft.client.gui.widget.TextFieldWidget;',
    'import net.minecraft.client.gui.Gui;': 'import net.minecraft.client.gui.AbstractGui;',
    'import net.minecraft.client.gui.IGuiEventListener;': 'import net.minecraft.client.gui.IGuiEventListener;',
    'import net.minecraft.client.multiplayer.WorldClient;': 'import net.minecraft.client.world.ClientWorld;',
    'import net.minecraft.network.NetworkManager;': 'import net.minecraft.network.NetworkManager;',
    'import net.minecraft.network.login.client.CPacketLoginStart;': 'import net.minecraft.network.login.client.CLoginStartPacket;',
    'import net.minecraft.network.login.server.SPacketEncryptionRequest;': 'import net.minecraft.network.login.server.SEncryptionRequestPacket;',
    'import net.minecraft.network.play.server.SPacketJoinGame;': 'import net.minecraft.network.play.server.SJoinGamePacket;',
    'import net.minecraft.network.NetHandlerLoginServer;': 'import net.minecraft.network.login.ServerLoginNetHandler;',
    'import net.minecraft.client.network.NetHandlerLoginClient;': 'import net.minecraft.client.network.login.ClientLoginNetHandler;',
    'import net.minecraft.client.network.NetHandlerPlayClient;': 'import net.minecraft.client.network.play.ClientPlayNetHandler;',
    'import net.minecraft.util.text.TextComponentTranslation;': 'import net.minecraft.util.text.TranslationTextComponent;',
    'import net.minecraft.util.text.ITextComponent;': 'import net.minecraft.util.text.ITextComponent;',
}

# Type renames in code (not just imports)
TYPE_MAP = {
    'GuiScreen': 'Screen',
    'GuiButton': 'Button',
    'GuiIngameMenu': 'IngameMenuScreen',
    'GuiMainMenu': 'MainMenuScreen',
    'GuiShareToLan': 'ShareToLanScreen',
    'GuiYesNo': 'ConfirmScreen',
    'GuiYesNoCallback': 'ConfirmScreen',
    'GuiSlot': 'ExtendedList',
    'GuiTextField': 'TextFieldWidget',
    'Gui ': 'AbstractGui ',
    'WorldClient': 'ClientWorld',
    'CPacketLoginStart': 'CLoginStartPacket',
    'SPacketEncryptionRequest': 'SEncryptionRequestPacket',
    'SPacketJoinGame': 'SJoinGamePacket',
    'NetHandlerLoginServer': 'ServerLoginNetHandler',
    'NetHandlerLoginClient': 'ClientLoginNetHandler',
    'NetHandlerPlayClient': 'ClientPlayNetHandler',
    'TextComponentTranslation': 'TranslationTextComponent',
}

count = 0
for root, dirs, files in os.walk(src_dir):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        fpath = os.path.join(root, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original = content
        
        # Replace imports
        for old_import, new_import in IMPORT_MAP.items():
            content = content.replace(old_import, new_import)
        
        # Replace type names in code (only for files we changed imports in)
        if content != original:
            for old_type, new_type in TYPE_MAP.items():
                # Only replace whole words, not inside strings or already-replaced imports
                # Use word boundary
                content = re.sub(r'\b' + re.escape(old_type) + r'\b', new_type, content)
        
        if content != original:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(content)
            rel = os.path.relpath(fpath, src_dir)
            print(f'Fixed: {rel}')
            count += 1

print(f'\nTotal files fixed: {count}')
