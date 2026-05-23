plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF 1.21.11 adapter — version-specific code, loader-agnostic"

loom {
    // Enable mixin AP + refmap generation.
    // Without refmap, Fabric can't remap Mojang method names to intermediary
    // names at runtime, causing "No refMap loaded" and mixin target errors.
    mixin { useLegacyMixinAp = true }
}

dependencies {
    // Shared pure-logic module
    api(project(":common"))

    // Minecraft — auto-downloaded by Loom
    "minecraft"(libs.minecraft12111)

    // Mojang official mappings — provides official→named (Mojang names)
    "mappings"(loom.officialMojangMappings())

    // Fabric mixin infrastructure (compile-only)
    compileOnlyApi(libs.fabric.loader)
    compileOnlyApi(libs.sponge.mixin)

    compileOnlyApi(libs.authlib)
    compileOnlyApi(libs.webrtc.java)
    compileOnlyApi(libs.brigadier)
    compileOnlyApi(libs.logging.mojang)
    compileOnlyApi(libs.datafixerupper)
    compileOnlyApi(libs.gson)
    compileOnlyApi(libs.jspecify)
    compileOnlyApi(libs.slf4j.api)
    compileOnlyApi(libs.log4j.api)
    compileOnlyApi(libs.netty.buffer)
    compileOnlyApi(libs.netty.transport)
    compileOnlyApi(libs.netty.common)
    compileOnlyApi(libs.netty.codec.base)
    compileOnlyApi(libs.netty.handler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
