package dev.msf.friends.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Invoker for the package-private 9-arg constructor of
 * {@link ClientHandshakePacketListenerImpl}.
 *
 * <p>Fabric Loom remaps all class/method references from Mojang → intermediary
 * at compile time, so this works transparently at runtime without any
 * explicit name mapping tables.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public interface ClientHandshakeInvoker {

    @Invoker("<init>")
    static ClientHandshakePacketListenerImpl msf$create(
            Connection connection,
            Minecraft mc,
            ServerData serverData,
            Screen parentScreen,
            boolean newWorld,
            Duration worldLoadTime,
            Consumer<?> statusListener,
            LevelLoadTracker levelLoadTracker,
            TransferState transferState) {
        throw new AssertionError();
    }
}
