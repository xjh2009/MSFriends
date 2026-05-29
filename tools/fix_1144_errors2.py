#!/usr/bin/env python3
"""Fix remaining 26 compilation errors for MC 1.14.4 Forge adaptation."""
import os, re, glob

BASE = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.14.4\forge\src\main\java\dev\msf\friends'

def fix_file(rel, transforms):
    path = os.path.join(BASE, rel)
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    orig = content
    for old, new in transforms:
        if old in content:
            content = content.replace(old, new)
        else:
            print(f'  WARNING: not found in {rel}: {old[:80]}...')
    if content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'  Modified: {rel}')
    else:
        print(f'  No changes: {rel}')

# ========== 1. Logging.get() -> Logging.logger() in all files ==========
for fp in glob.glob(os.path.join(BASE, '**', '*.java'), recursive=True):
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    orig = content
    content = content.replace('Logging.get()', 'Logging.logger()')
    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'  Fixed Logging.get() in {os.path.relpath(fp, BASE)}')

# ========== 2. FriendsScreen.java ==========
fix_file('screen/FriendsScreen.java', [
    # Remove duplicate accept stub
    ('    @Override public void accept(boolean result) { /* handled inline */ }\n', ''),
    # Remove duplicate no-arg constructor
    ('''    public FriendsScreen() {
        super(new TranslationTextComponent("screen.msf_friends.friends"));
    }
    static final int SKIN_SIZE = 24;''',
     '    static final int SKIN_SIZE = 24;'),
    # Fix AbstractList.AbstractListEntry -> plain class
    ('''    static abstract class BaseEntry extends AbstractList.AbstractListEntry<BaseEntry> {''',
     '''    static abstract class BaseEntry {'''),
    # Fix Widget.width/height protected access
    ('''                    unselectedTab.x + unselectedTab.width, unselectedTab.y + unselectedTab.height, 0x99000000);''',
     '''                    unselectedTab.x + unselectedTab.getWidth(), unselectedTab.y + unselectedTab.getHeight(), 0x99000000);'''),
    # Fix TextFieldWidget constructor - add empty string
    ('''new TextFieldWidget(this.font, this.marginX() + 28, 72, 152, 20)''',
     '''new TextFieldWidget(this.font, this.marginX() + 28, 72, 152, 20, "")'''),
    # Remove @Override on close() since Screen doesn't have close() 
    ('''    @Override
    public void close() {''',
     '''    /** Called when Done button is pressed. */
    public void close() {'''),
])

# ========== 3. FriendsPlayerList - add renderItem ==========
path = os.path.join(BASE, 'screen', 'FriendsScreen.java')
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_fpl = '''    static class FriendsPlayerList extends SlotGui {
        private final List<BaseEntry> entries = new ArrayList<BaseEntry>();

        public FriendsPlayerList(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        public void setEntries(List<BaseEntry> newEntries) {
            this.entries.clear();
            this.entries.addAll(newEntries);
        }

        public int getItemCount() { return entries.size(); }

        @Override
        public int getRowWidth() { return 200; }

        @Override
        protected int getScrollbarPosition() { return this.x0 + this.width / 2 + 100; }

        @Override
        protected boolean isSelectedItem(int index) { return false; }

        @Override
        protected int getMaxPosition() { return this.getItemCount() * this.itemHeight; }

        @Override
        protected void renderBackground() { }
    }'''

new_fpl = '''    static class FriendsPlayerList extends SlotGui {
        private final List<BaseEntry> entries = new ArrayList<BaseEntry>();

        public FriendsPlayerList(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        public void setEntries(List<BaseEntry> newEntries) {
            this.entries.clear();
            this.entries.addAll(newEntries);
        }

        @Override
        public int getItemCount() { return entries.size(); }

        @Override
        public int getRowWidth() { return 200; }

        @Override
        protected int getScrollbarPosition() { return this.x0 + this.width / 2 + 100; }

        @Override
        protected boolean isSelectedItem(int index) { return false; }

        @Override
        protected int getMaxPosition() { return this.getItemCount() * this.itemHeight; }

        @Override
        protected void renderBackground() { }

        @Override
        protected void renderItem(int index, int x, int y, int rowWidth, int rowHeight, int mouseX, int mouseY, float delta) {
            if (index >= 0 && index < entries.size()) {
                entries.get(index).render(x, y, rowHeight, mouseX, mouseY, delta);
            }
        }
    }'''

if old_fpl in content:
    content = content.replace(old_fpl, new_fpl)
    print('  Fixed FriendsPlayerList in FriendsScreen.java')
else:
    print('  WARNING: FriendsPlayerList not found for replacement')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# ========== 4. KeyBindingMixin ==========
fix_file('mixin/KeyBindingMixin.java', [
    ('mc.currentScreen', 'mc.field_71462_r'),
])

# ========== 5. PauseScreenMixin ==========
fix_file('mixin/PauseScreenMixin.java', [
    ('''public abstract class PauseScreenMixin extends Screen {

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */''',
     '''public abstract class PauseScreenMixin extends Screen {
    private PauseScreenMixin() {
        super(new net.minecraft.util.text.StringTextComponent("Pause"));
    }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */'''),
    ('btn.getMessage().getString()', 'btn.getMessage()'),
])

# ========== 6. ShareToLanScreenMixin ==========
fix_file('mixin/ShareToLanScreenMixin.java', [
    ('''public abstract class ShareToLanScreenMixin extends Screen {

    @Shadow @Final private Screen lastScreen;''',
     '''public abstract class ShareToLanScreenMixin extends Screen {
    private ShareToLanScreenMixin() {
        super(new net.minecraft.util.text.StringTextComponent("Share to LAN"));
    }

    @Shadow @Final private Screen lastScreen;'''),
    # Fix Button constructor - remove id=9999
    ('''Button scopeBtn = new Button(9999, x, 160, 60, 20,
                msf$label(this.msf$scope)) {
            @Override
            public void onClick(double mouseX, double mouseY) {''',
     '''Button scopeBtn = new Button(x, 160, 60, 20,
                msf$label(this.msf$scope), b -> {}) {
            @Override
            public void onPress() {'''),
])

# ========== 7. TitleScreenMixin ==========
fix_file('mixin/TitleScreenMixin.java', [
    ('''public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static final ResourceLocation MSF$FRIENDS_ICON''',
     '''public abstract class TitleScreenMixin extends Screen {
    private TitleScreenMixin() {
        super(new net.minecraft.util.text.StringTextComponent("Title"));
    }

    @Unique
    private static final ResourceLocation MSF$FRIENDS_ICON'''),
])

# ========== 8. PlayerSkinResolver ==========
fix_file('screen/PlayerSkinResolver.java', [
    ('SkinManager.SkinAvailableCallback', 'SkinManager.ISkinAvailableCallback'),
])

print('\nDone! Run build to check.')

