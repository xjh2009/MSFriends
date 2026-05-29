package dev.msf.friends.mixin.accessor;

import net.minecraft.client.gui.screen.option.SimpleOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOptionsScreen.class)
public interface SimpleOptionsScreenAccessor {
    @Accessor("buttonList")
    OptionListWidget msf$getButtonList();
}
