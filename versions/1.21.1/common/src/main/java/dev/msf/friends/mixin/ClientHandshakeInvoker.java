package dev.msf.friends.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Invoker for the package-private constructor of
 * {@link ClientHandshakePacketListenerImpl}.
 *
 * <p>Fabric Loom remaps all class/method/field references from Mojang → intermediary
 * at compile time, so this works transparently at runtime without any
 * explicit name-mapping tables.
 *
 * <p>MC 1.21.1 constructor has 8 parameters (no LevelLoadTracker).</p>
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public interface ClientHandshakeInvoker {

    /**
     * Invoke the 8-arg constructor. Parameter types must match exactly.
     * The 8th parameter is {@link TransferState} (null for non-transfer connections).
     */
    @Invoker("<init>")
    static ClientHandshakePacketListenerImpl msf$create(
            Connection connection,
            Minecraft mc,
            ServerData serverData,
            Screen parentScreen,
            boolean isTransfer,
            Duration timeout,
            Consumer<?> statusListener,
            TransferState transferState) {
        throw new AssertionError();
    }
}
