"""Fix 1.14.4 MCP names in source files (copied from 1.15.2)."""
import os, re, glob

BASE = os.path.join(os.path.dirname(__file__), "..", "versions", "1.14.4", "forge", "src")
if not os.path.isdir(BASE):
    # Try absolute
    BASE = r"C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.14.4\forge\src"

# ── 1. Import replacements ──
IMPORT_REPLACEMENTS = [
    # Screen classes
    ("import net.minecraft.client.gui.GuiScreen;\n",
     "import net.minecraft.client.gui.screen.Screen;\n"),
    ("import net.minecraft.client.gui.GuiIngameMenu;\n",
     "import net.minecraft.client.gui.screen.IngameMenuScreen;\n"),
    ("import net.minecraft.client.gui.GuiShareToLan;\n",
     "import net.minecraft.client.gui.screen.ShareToLanScreen;\n"),
    ("import net.minecraft.client.gui.GuiMainMenu;\n",
     "import net.minecraft.client.gui.screen.MainMenuScreen;\n"),
    # Widget classes
    ("import net.minecraft.client.gui.GuiButton;\n",
     "import net.minecraft.client.gui.widget.button.Button;\n"),
    ("import net.minecraft.client.gui.GuiTextField;\n",
     "import net.minecraft.client.gui.widget.TextFieldWidget;\n"),
    ("import net.minecraft.client.gui.GuiSlot;\n",
     "import net.minecraft.client.gui.SlotGui;\n"),
    # Confirm
    ("import net.minecraft.client.gui.GuiYesNo;\n",
     "import net.minecraft.client.gui.screen.ConfirmScreen;\n"),
    ("import net.minecraft.client.gui.GuiYesNoCallback;\n",
     "import it.unimi.dsi.fastutil.booleans.BooleanConsumer;\n"),
    # GlStateManager
    ("import net.minecraft.client.renderer.GlStateManager;\n",
     "import com.mojang.blaze3d.platform.GlStateManager;\n"),
    # TextComponent
    ("import net.minecraft.util.text.TextComponentTranslation;\n",
     "import net.minecraft.util.text.TranslationTextComponent;\n"),
    # Network classes
    ("import net.minecraft.network.login.client.CPacketLoginStart;\n",
     "import net.minecraft.network.login.client.CLoginStartPacket;\n"),
    ("import net.minecraft.network.login.server.SPacketEncryptionRequest;\n",
     "import net.minecraft.network.login.server.SEncryptionRequestPacket;\n"),
    ("import net.minecraft.client.network.NetHandlerLoginClient;\n",
     "import net.minecraft.client.network.login.ClientLoginNetHandler;\n"),
    ("import net.minecraft.client.network.NetHandlerPlayClient;\n",
     "import net.minecraft.client.network.play.ClientPlayNetHandler;\n"),
    ("import net.minecraft.network.play.server.SPacketJoinGame;\n",
     "import net.minecraft.network.play.server.SJoinGamePacket;\n"),
    ("import net.minecraft.network.NetHandlerLoginServer;\n",
     "import net.minecraft.network.login.ServerLoginNetHandler;\n"),
]

# ── 2. In-code replacements (class/type names in body) ──
# Only for exact whole-word matches
CODE_REPLACEMENTS = [
    # Screen class names
    ("GuiScreen", "Screen"),
    ("GuiIngameMenu", "IngameMenuScreen"),
    ("GuiShareToLan", "ShareToLanScreen"),
    ("GuiMainMenu", "MainMenuScreen"),
    # Widget class names
    ("GuiButtonExt", "Button"),  # GuiButtonExt extends Button in 1.14.4
    ("GuiButton", "Button"),
    ("GuiTextField", "TextFieldWidget"),
    ("GuiSlot", "SlotGui"),
    # Confirm
    ("GuiYesNo", "ConfirmScreen"),
    ("GuiYesNoCallback", "BooleanConsumer"),
    # TextComponent
    ("TextComponentTranslation", "TranslationTextComponent"),
    # Network classes
    ("CPacketLoginStart", "CLoginStartPacket"),
    ("SPacketEncryptionRequest", "SEncryptionRequestPacket"),
    ("NetHandlerLoginClient", "ClientLoginNetHandler"),
    ("NetHandlerPlayClient", "ClientPlayNetHandler"),
    ("SPacketJoinGame", "SJoinGamePacket"),
    ("NetHandlerLoginServer", "ServerLoginNetHandler"),
    # Field names: this.mc -> this.minecraft (but not in strings/comments with "mc.")
    # fontRenderer -> font (but not "fontRendererObj")
]

# ── 3. Field/method replacements ──
FIELD_REPLACEMENTS = [
    # Screen field names
    ("this.fontRenderer", "this.font"),
    ("this.mc.", "this.minecraft."),
    ("this.mc\n", "this.minecraft\n"),
    ("this.mc;", "this.minecraft;"),
    ("this.mc)", "this.minecraft)"),
    # Method names
    ("initGui()", "init()"),
    ("onGuiClosed()", "onClose()"),
    ("drawScreen(", "render("),
    ("drawCenteredString(", "drawCenteredString("),  # keep - same in 1.14.4
    ("getChildren()", "children"),  # it's a field in 1.14.4
    # Mixin method targets
    ('method = "initGui"', 'method = "init"'),
    ('method = "drawScreen"', 'method = "render"'),
    # GuiSlot method names
    ("drawSlot(", "renderItem("),
    ("drawSelectionBox(", "renderList("),
    # ToastGui -> IToast changes
    ("GuiToast", "ToastGui"),
    # mc.fontRenderer -> mc.font
    ("mc.fontRenderer", "minecraft.font"),
    ("minecraft.fontRenderer", "minecraft.font"),
    # Minecraft.getInstance() - keep as-is (same in both)
    # displayGuiScreen -> func_147108_a (SRG name - need to check)
    # Actually in 1.14.4 recompiled jar with MCP, it's also displayGuiScreen
    # so no change needed
]

# New: IToast method name changes
# In 1.15.2: draw(ToastGui, long) -> in 1.14.4: func_193653_a(ToastGui, long)
# BUT since we compile against recompiled jar which has MCP names...
# let's check what the method is called in 1.14.4
# From IToast source: IToast.Visibility func_193653_a(ToastGui p_193653_1_, long p_193653_2_)
# This is the SRG name! So the MCP mapped name would be "draw" in 1.14.4 too.
# Actually, wait - in 1.14.4 the recompiled jar might have the SRG name for this method
# because the MCP snapshot 20190601 might not have a mapping for it.
# The FriendToast uses: draw(GuiToast toastGui, long startTime)
# In 1.14.4 IToast: func_193653_a(ToastGui, long)
# So if MCP didn't map it, we need to use func_193653_a.
# BUT the FriendToast implements IToast, so we override the interface method.
# Let's check if the method has an MCP name in the injected sources.
# From the injected-sources.jar, the IToast shows:
# IToast.Visibility func_193653_a(ToastGui p_193653_1_, long p_193653_2_);
# So the method IS still SRG-named "func_193653_a" in 1.14.4.
# We need to change "draw" to "func_193653_a" in FriendToast.

# SlotGui changes: In 1.14.4, SlotGui uses different method names
# drawSlot -> not sure of exact name, need to check
# Actually from 1.14.4 SlotGui source, the abstract method is:
# protected abstract void func_192637_a(int, int, int, int, int, int, float, int, int)
# which is "renderItem" or "drawSlot" depending on MCP mappings

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Apply import replacements first
    for old, new in IMPORT_REPLACEMENTS:
        content = content.replace(old, new)
    
    # Apply code replacements (word-boundary)
    for old, new in CODE_REPLACEMENTS:
        # Use word boundary to avoid partial matches
        content = re.sub(r'\b' + re.escape(old) + r'\b', new, content)
    
    # Apply field replacements
    for old, new in FIELD_REPLACEMENTS:
        content = content.replace(old, new)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# Fix all Java files
changed = []
for java_file in glob.glob(os.path.join(BASE, "**", "*.java"), recursive=True):
    if fix_file(java_file):
        changed.append(os.path.relpath(java_file, BASE))

print(f"Fixed {len(changed)} files:")
for f in changed:
    print(f"  {f}")
