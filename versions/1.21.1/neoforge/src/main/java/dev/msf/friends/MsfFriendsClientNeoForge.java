package dev.msf.friends;

import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * 客户端事件处理：tick 检测按键。
 */
@EventBusSubscriber(modid = MsfFriendsConstants.MOD_ID, value = Dist.CLIENT)
public final class MsfFriendsClientNeoForge {
    private static final Logger LOGGER = Logging.get();

    /** 每 tick 检测按键（游戏总线事件）。 */
    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        while (MsfKeyBindings.OPEN_FRIENDS.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                var boot = MsfFriendsBoot.get();
                if (boot != null && boot.social() != null) {
                    mc.setScreen(new FriendsScreen(null));
                } else {
                    LOGGER.warn("[client] Friends screen requested but social manager not ready yet");
                }
            }
        }
    }

    private MsfFriendsClientNeoForge() {}
}
