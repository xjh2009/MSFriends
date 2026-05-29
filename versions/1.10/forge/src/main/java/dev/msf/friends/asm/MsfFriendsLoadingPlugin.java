package dev.msf.friends.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

/**
 * FML core plugin that bootstraps Mixin for Forge 1.10.
 *
 * <p>On Forge 1.10, Mixin support requires an {@link IFMLLoadingPlugin} that
 * calls {@link MixinBootstrap#init()} and registers our mixin config during
 * construction (before the FML state machine reaches "LOADING").
 */
@IFMLLoadingPlugin.MCVersion("1.10.2")
@IFMLLoadingPlugin.Name("MSF Friends Loading Plugin")
@IFMLLoadingPlugin.SortingIndex(0)
public class MsfFriendsLoadingPlugin implements IFMLLoadingPlugin {

    public MsfFriendsLoadingPlugin() {
        MixinBootstrap.init();
        Mixins.addConfiguration("msf-friends.mixins.json");
        MixinEnvironment.getDefaultEnvironment().setObfuscationContext(
                MixinEnvironment.Side.CLIENT == MixinEnvironment.getCurrentEnvironment().getSide()
                        ? "searge" : "searge"
        );
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
