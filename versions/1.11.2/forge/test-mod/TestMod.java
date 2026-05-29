package dev.msf.test;

import net.minecraftforge.fml.common.Mod;

@Mod(modid = "testmod", name = "Test Mod", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.11,1.12)")
public class TestMod {
    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        System.out.println("[TESTMOD] PreInit!");
    }
}
