package dev.msf.friends;

import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;

/**
 * 客户端事件处理：注册按键绑定 + tick 检测按键。
 *
 * 此版本 @EventBusSubscriber 没有 bus 参数，
 * IModBusEvent（如 RegisterKeyMappingsEvent）自动路由到 mod 总线，
 * 普通事件（如 ClientTickEvent）路由到游戏总线。
 */
@EventBusSubscriber(modid = MsfFriendsConstants.MOD_ID, value = Dist.CLIENT)
public final class MsfFriendsClientNeoForge {
    private static final Logger LOGGER = Logging.get();

    /** 注册按键绑定（IModBusEvent，自动路由到 mod 总线）。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        LOGGER.info("[client] Registering key mappings");
        event.register(MsfKeyBindings.OPEN_FRIENDS);
    }

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
