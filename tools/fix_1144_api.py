#!/usr/bin/env python3
"""Deep API fix for Forge 1.14.4 (MCP snapshot 20190601-1.14.2).

Key differences from 1.15.2 MCP:
  - Gui class does NOT exist. Use AbstractGui (fill, blit)
  - Minecraft fields are SRG: field_71466_p (fontRenderer), field_71441_e (world), field_71439_g (player), field_71462_r (currentScreen)
  - Screen.font exists (MCP mapped)
  - No addScheduledTask — use execute(Runnable) from ThreadTaskExecutor
  - IToast.draw() IS MCP-mapped
  - drawScaledCustomSizeModalRect → AbstractGui.blit (static)
  - drawRect → AbstractGui.fill (static)
  - drawDefaultBackground → renderBackground
  - doesGuiPauseGame → isPauseScreen
  - Button constructor: (int x, int y, int w, int h, String msg, IPressable) — no id param
  - GuiButtonExt: (int x, int y, int w, int h, String msg, IPressable)
  - TextFieldWidget: (FontRenderer, int x, int y, int w, int h, String) — no id param
  - Widget has getMessage()/setMessage(), no displayString field
  - ConfirmScreen(BooleanConsumer, ITextComponent, ITextComponent)
  - DisconnectedScreen(Screen, String, ITextComponent)
  - Widget.width/height are protected, use getWidth()/getHeight()
  - Widget has: x, y, active, visible as public fields
  - loadWorld(ClientWorld) not loadWorld(WorldClient)
"""

import os
import re
import glob

BASE = os.path.join(os.path.dirname(__file__), '..', 'versions', '1.14.4', 'forge', 'src', 'main', 'java', 'dev', 'msf', 'friends')


def fix_file(rel_path, replacements):
    """Apply multiple (old, new) replacements to a file."""
    path = os.path.join(BASE, rel_path)
    if not os.path.exists(path):
        print(f"  SKIP (not found): {rel_path}")
        return 0
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    for old, new in replacements:
        if old in content:
            content = content.replace(old, new)
    if content != original:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        count = sum(1 for old, _ in replacements if old in original)
        print(f"  FIXED {rel_path} ({count} replacements)")
        return 1
    else:
        print(f"  OK (no changes): {rel_path}")
        return 0


def fix_friends_screen():
    """Fix FriendsScreen.java — the biggest file."""
    path = os.path.join(BASE, 'screen', 'FriendsScreen.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix imports
    content = content.replace(
        'import net.minecraft.client.gui.Gui;',
        'import net.minecraft.client.gui.AbstractGui;\nimport net.minecraft.util.text.StringTextComponent;'
    )

    # 2. Fix TextFieldWidget constructor — remove id=0 param
    content = content.replace(
        'this.searchBox = new TextFieldWidget(0, this.font, this.marginX() + 28, 72, 152, 20);',
        'this.searchBox = new TextFieldWidget(this.font, this.marginX() + 28, 72, 152, 20, "");'
    )

    # 3. Fix Gui.drawRect → AbstractGui.fill
    content = content.replace('Gui.drawRect(', 'AbstractGui.fill(')

    # 4. Fix Gui.drawScaledCustomSizeModalRect → AbstractGui.blit
    # Background blit: drawScaledCustomSizeModalRect(x, y, u, v, srcW, srcH, drawW, drawH, texW, texH)
    # When srcW==drawW and srcH==drawH: AbstractGui.blit(x, y, u, v, w, h, texW, texH)
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(mx, 64, 0, 0, BG_WIDTH, this.windowHeight(),\n                BG_WIDTH, this.windowHeight(), 256, 256);',
        'AbstractGui.blit(mx, 64, 0f, 0f, BG_WIDTH, this.windowHeight(), 256, 256);'
    )

    # 5. Fix addScheduledTask → execute
    content = content.replace('.addScheduledTask(', '.execute(')

    # 6. Fix doesGuiPauseGame → isPauseScreen
    content = content.replace('doesGuiPauseGame()', 'isPauseScreen()')

    # 7. Fix ConfirmScreen constructor calls — need (BooleanConsumer, ITextComponent, ITextComponent)
    # Pattern: new ConfirmScreen(screen, trStr(...), trStr(..., ...), 0)
    # The 4th arg (0) needs to be removed, and trStr returns String not ITextComponent
    content = content.replace(
        '''this.minecraft.displayGuiScreen(new ConfirmScreen(screen,
                                trStr("screen.msf_friends.friends.confirm_remove.title"),
                                trStr("screen.msf_friends.friends.confirm_remove.message", data.name()),
                                0));''',
        '''this.minecraft.displayGuiScreen(new ConfirmScreen(screen,
                                new StringTextComponent(trStr("screen.msf_friends.friends.confirm_remove.title")),
                                new StringTextComponent(trStr("screen.msf_friends.friends.confirm_remove.message", data.name()))));'''
    )

    # 8. Fix minecraft.font → screen.font in inner classes (BaseEntry, FriendEntry, etc.)
    # In inner classes, `minecraft.font` won't work because `font` is on Screen, not Minecraft.
    # But they have a `screen` field, so use `screen.font`
    content = content.replace('minecraft.font.drawString(', 'screen.font.drawString(')

    # 9. Fix drawScaledCustomSizeModalRect in inner class renderFace
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, SKIN_SIZE, SKIN_SIZE, 64, 64);',
        'AbstractGui.blit(x, y, SKIN_SIZE, SKIN_SIZE, 8f, 8f, 8, 8, 64, 64);'
    )
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, SKIN_SIZE, SKIN_SIZE, 64, 64);',
        'AbstractGui.blit(x, y, SKIN_SIZE, SKIN_SIZE, 40f, 8f, 8, 8, 64, 64);'
    )

    # 10. Fix SlotGui method names
    # renderItem params in SlotGui: (int slotIndex, int x, int y, int height, int mouseX, int mouseY, float delta)
    # The override should match
    content = content.replace(
        'protected boolean isSelected(int index)',
        'protected boolean isSelectedItem(int index)'
    )

    # 11. Fix getContentHeight → getMaxPosition
    content = content.replace(
        'protected int getContentHeight()',
        'protected int getMaxPosition()'
    )

    # 12. Fix getListWidth → getRowWidth
    content = content.replace(
        'public int getListWidth()',
        'public int getRowWidth()'
    )

    # 13. Fix getScrollBarX → getScrollbarPosition
    content = content.replace(
        'protected int getScrollBarX()',
        'protected int getScrollbarPosition()'
    )

    # 14. Fix left/width references in SlotGui (x0, x1, y0, y1 in 1.14.4)
    # In FriendsPlayerList: this.left → this.x0, this.width is fine (protected)
    content = content.replace(
        'return this.left + this.width / 2 + 100;',
        'return this.x0 + this.width / 2 + 100;'
    )

    # 15. Fix Minecraft.world → Minecraft.field_71441_e
    content = content.replace('this.minecraft.world == null', 'this.minecraft.field_71441_e == null')

    # 16. Fix this.minecraft.currentScreen → this.minecraft.field_71462_r (in P2PConnectScreen usage)
    # Actually in FriendsScreen, `minecraft.currentScreen` doesn't appear. Let me check.

    # 17. Fix drawDefaultBackground → renderBackground
    content = content.replace('this.drawDefaultBackground()', 'this.renderBackground()')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED screen/FriendsScreen.java")


def fix_friend_toast():
    """Fix FriendToast.java."""
    path = os.path.join(BASE, 'screen', 'FriendToast.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix import Gui → AbstractGui
    content = content.replace(
        'import net.minecraft.client.gui.Gui;',
        'import net.minecraft.client.gui.AbstractGui;'
    )

    # 2. Fix Gui.drawScaledCustomSizeModalRect → AbstractGui.blit
    # Background: drawScaledCustomSizeModalRect(0, 0, 0, 0, TOAST_WIDTH, TOAST_HEIGHT, TOAST_WIDTH, TOAST_HEIGHT, 256, 256)
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, TOAST_WIDTH, TOAST_HEIGHT, TOAST_WIDTH, TOAST_HEIGHT, 256, 256);',
        'AbstractGui.blit(0, 0, 0f, 0f, TOAST_WIDTH, TOAST_HEIGHT, 256, 256);'
    )

    # 3. Fix face rendering
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, s, s, 64, 64);',
        'AbstractGui.blit(x, y, s, s, 8f, 8f, 8, 8, 64, 64);'
    )
    content = content.replace(
        'Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, s, s, 64, 64);',
        'AbstractGui.blit(x, y, s, s, 40f, 8f, 8, 8, 64, 64);'
    )

    # 4. Fix minecraft.font → mc.field_71466_p (toast is not a Screen, no font field)
    content = content.replace(
        'FontRenderer fontRenderer = minecraft.font;',
        'FontRenderer fontRenderer = mc.field_71466_p;'
    )

    # 5. Fix fontRenderer.drawString calls (add float casts for x,y params)
    # drawString(String, float, float, int)
    content = content.replace(
        "fontRenderer.drawString(title.getFormattedText(), textX, titleY, 0xFFFFFFFF);",
        "fontRenderer.drawString(title.getFormattedText(), (float)textX, (float)titleY, 0xFFFFFFFF);"
    )
    content = content.replace(
        "fontRenderer.drawString(description.getFormattedText(), textX, titleY + LINE_SPACING, 0xFFAAAAAA);",
        "fontRenderer.drawString(description.getFormattedText(), (float)textX, (float)(titleY + LINE_SPACING), 0xFFAAAAAA);"
    )

    # 6. Fix drawRect → fill (not used in FriendToast, but check)
    content = content.replace('Gui.drawRect(', 'AbstractGui.fill(')

    # 7. IToast.draw() is MCP-mapped in 1.14.4, so no change needed

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED screen/FriendToast.java")


def fix_icon_button_widget():
    """Fix IconButtonWidget.java."""
    path = os.path.join(BASE, 'screen', 'IconButtonWidget.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix import — no Forge Button class in 1.14.4, use GuiButtonExt
    content = content.replace(
        'import net.minecraftforge.fml.client.config.Button;',
        'import net.minecraftforge.fml.client.config.GuiButtonExt;'
    )

    # 2. Fix class extends
    content = content.replace(
        'public class IconButtonWidget extends Button {',
        'public class IconButtonWidget extends GuiButtonExt {'
    )

    # 3. Fix constructor — GuiButtonExt uses (int, int, int, int, String, IPressable)
    # Replace the constructor call: super(ID_COUNTER.getAndIncrement(), x, y, width, height, message)
    content = content.replace(
        'super(ID_COUNTER.getAndIncrement(), x, y, width, height, message);',
        'super(x, y, width, height, message, b -> {});'
    )

    # 4. Fix Gui.drawScaledCustomSizeModalRect → AbstractGui.blit
    content = content.replace(
        'import net.minecraft.client.gui.Gui;',
        'import net.minecraft.client.gui.AbstractGui;'
    )
    # But wait, IconButtonWidget doesn't import Gui explicitly... it uses the FQN
    content = content.replace(
        'net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(\n                    iconX, iconY, 0, 0,\n                    this.iconWidth, this.iconHeight,\n                    this.iconWidth, this.iconHeight,\n                    this.iconWidth, this.iconHeight);',
        'AbstractGui.blit(\n                    iconX, iconY, 0f, 0f,\n                    this.iconWidth, this.iconHeight,\n                    this.iconWidth, this.iconHeight);'
    )

    # 5. Fix displayString references — in 1.14.4 Widget has getMessage()/setMessage()
    content = content.replace('this.displayString', 'this.getMessage()')
    # For assignment, need setMessage()
    content = content.replace(
        'String oldMessage = this.getMessage();',
        'String oldMessage = this.getMessage();'
    )
    content = content.replace(
        'this.getMessage() = "";',
        'this.setMessage("");'
    )
    content = content.replace(
        'this.getMessage() = oldMessage;',
        'this.setMessage(oldMessage);'
    )

    # 6. Fix width/height — protected in Widget, need getWidth()/getHeight()
    # In IconButtonWidget, `this.width` and `this.height` are accessed from within the class,
    # which extends Widget, so protected access should work.

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED screen/IconButtonWidget.java")


def fix_simple_button():
    """Fix SimpleButton.java — remove id parameter."""
    path = os.path.join(BASE, 'screen', 'SimpleButton.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Button constructor in 1.14.4: (int x, int y, int w, int h, String msg, IPressable onPress)
    # Add IPressable import
    content = content.replace(
        'import net.minecraft.client.gui.widget.button.Button;',
        'import net.minecraft.client.gui.widget.button.Button;\nimport net.minecraft.client.gui.widget.button.Button.IPressable;'
    )

    # Remove AtomicInteger import and ID_COUNTER (no longer needed)
    content = content.replace(
        "import java.util.concurrent.atomic.AtomicInteger;\n\n",
        ""
    )
    content = content.replace(
        '    private static final AtomicInteger ID_COUNTER = new AtomicInteger(10000);\n',
        ''
    )

    # Fix constructor
    content = content.replace(
        '        super(ID_COUNTER.getAndIncrement(), x, y, width, height, message);\n        this.action = action;',
        '        super(x, y, width, height, message, b -> {});\n        this.action = action;'
    )

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED screen/SimpleButton.java")


def fix_p2p_connect_screen():
    """Fix P2PConnectScreen.java."""
    path = os.path.join(BASE, 'screen', 'P2PConnectScreen.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix import — GuiDisconnected → DisconnectedScreen
    content = content.replace(
        'import net.minecraft.client.gui.GuiDisconnected;',
        'import net.minecraft.client.gui.screen.DisconnectedScreen;'
    )

    # 2. Fix GuiDisconnected → DisconnectedScreen usage
    content = content.replace('GuiDisconnected', 'DisconnectedScreen')

    # 3. Fix addScheduledTask → execute
    content = content.replace('.addScheduledTask(', '.execute(')

    # 4. Fix drawDefaultBackground → renderBackground
    content = content.replace('this.drawDefaultBackground()', 'this.renderBackground()')

    # 5. Fix minecraft.currentScreen → minecraft.field_71462_r
    content = content.replace('minecraft.currentScreen == this', 'minecraft.field_71462_r == this')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED screen/P2PConnectScreen.java")


def fix_forge_reflect():
    """Fix ForgeReflect.java class name mappings."""
    path = os.path.join(BASE, 'bridge', 'ForgeReflect.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Update class paths to use correct 1.14.4 package locations
    replacements = [
        # Screen and screen classes
        ('"net.minecraft.client.gui.Screen",', '"net.minecraft.client.gui.screen.Screen",'),
        ('"net.minecraft.client.gui.MainMenuScreen",', '"net.minecraft.client.gui.screen.MainMenuScreen",'),
        ('"net.minecraft.client.gui.IngameMenuScreen",', '"net.minecraft.client.gui.screen.IngameMenuScreen",'),
        ('"net.minecraft.client.gui.ShareToLanScreen",', '"net.minecraft.client.gui.screen.ShareToLanScreen",'),
        ('"net.minecraft.client.gui.Button",', '"net.minecraft.client.gui.widget.button.Button",'),
        ('"net.minecraft.client.gui.TextFieldWidget",', '"net.minecraft.client.gui.widget.TextFieldWidget",'),
        # Login classes
        ('"net.minecraft.network.login.client.CLoginStartPacket",', '"net.minecraft.network.login.client.CLoginStartPacket",'),
        ('"net.minecraft.client.network.ClientLoginNetHandler",', '"net.minecraft.client.network.login.ClientLoginNetHandler",'),
        ('"net.minecraft.network.ServerLoginNetHandler",', '"net.minecraft.network.login.ServerLoginNetHandler",'),
    ]

    for old, new in replacements:
        content = content.replace(old, new)

    # Fix SRG method fallbacks — these should be for 1.14.4
    # func_71410_x → getInstance (MCP mapped in 1.14.4)
    # func_147108_a → displayGuiScreen (MCP mapped in 1.14.4)
    # func_179290_a → sendPacket (need to verify)
    # func_71206_a → shareToLAN
    # func_184103_al → getPlayerList
    # These SRG fallbacks should stay as-is since they provide production compat

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED bridge/ForgeReflect.java")


def fix_guest_connection_mixin():
    """Fix GuestConnectionMixin.java — WorldClient → ClientWorld."""
    path = os.path.join(BASE, 'mixin', 'GuestConnectionMixin.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Fix mixin target: loadWorld(WorldClient) → loadWorld(ClientWorld)
    content = content.replace(
        'import net.minecraft.client.Minecraft;',
        'import net.minecraft.client.Minecraft;\nimport net.minecraft.client.world.ClientWorld;'
    )
    content = content.replace(
        'net.minecraft.client.multiplayer.WorldClient',
        'net.minecraft.client.world.ClientWorld'
    )
    content = content.replace(
        'Lnet/minecraft/client/multiplayer/WorldClient;',
        'Lnet/minecraft/client/world/ClientWorld;'
    )

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED mixin/GuestConnectionMixin.java")


def fix_pause_screen_mixin():
    """Fix PauseScreenMixin.java."""
    path = os.path.join(BASE, 'mixin', 'PauseScreenMixin.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix displayString → getMessage()
    content = content.replace(
        'String msgStr = btn.displayString;',
        'String msgStr = btn.getMessage();'
    )

    # 2. Widget.width is protected, but we access it from outside. Use getWidth() instead.
    # optionsBtn.width → optionsBtn.getWidth() — but wait, PauseScreenMixin extends Screen
    # which is in a different package from Widget... Actually in the mixin context, we
    # might have access. Let me check if the code uses btn.width or btn.getWidth().
    # The code does: btn.y, optionsBtn.setWidth, optionsBtn.x — these are public on Widget.

    # 3. The `children` field access should work (protected in Screen, accessed from subclass)

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED mixin/PauseScreenMixin.java")


def fix_connection_bridge():
    """Fix ConnectionBridge.java — WorldClient → ClientWorld."""
    path = os.path.join(BASE, 'bridge', 'ConnectionBridge.java')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Fix WorldClient → ClientWorld
    content = content.replace(
        '"net.minecraft.client.multiplayer.WorldClient"',
        '"net.minecraft.client.world.ClientWorld"'
    )
    content = content.replace(
        'worldClientClass',
        'clientWorldClass'
    )

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("  FIXED bridge/ConnectionBridge.java")


def main():
    print("=== Fixing Forge 1.14.4 API differences ===")
    print()

    fix_friends_screen()
    fix_friend_toast()
    fix_icon_button_widget()
    fix_simple_button()
    fix_p2p_connect_screen()
    fix_forge_reflect()
    fix_guest_connection_mixin()
    fix_pause_screen_mixin()
    fix_connection_bridge()

    print()
    print("=== All API fixes applied ===")


if __name__ == '__main__':
    main()
