package dev.msf.test;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "test_mod", name = "Test Mod", version = "1.0", acceptedMinecraftVersions = "[1.11,1.12)")
public class TestMod {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("[TEST] TestMod preInit!");
    }
}
