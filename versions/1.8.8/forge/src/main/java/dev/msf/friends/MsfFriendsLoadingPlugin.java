package dev.msf.friends;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.util.Map;

/**
 * FML coremod loading plugin for MC 1.8.8 Forge.
 * Simplified: no Mixin bootstrap needed (we use reflection instead).
 */
@IFMLLoadingPlugin.MCVersion("1.8.8")
@IFMLLoadingPlugin.Name("MsfFriends")
public class MsfFriendsLoadingPlugin implements IFMLLoadingPlugin {
    private static final Logger LOGGER = Logging.get();

    public MsfFriendsLoadingPlugin() {
        LOGGER.info("[coremod] MsfFriends loading plugin initialized");
    }

    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
