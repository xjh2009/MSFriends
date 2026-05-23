plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF 1.20.1 adapter — version-specific code, loader-agnostic"

loom {
    // Enable mixin AP + refmap generation for Yarn-mapped versions.
    // Without refmap, Fabric can't remap intermediary method names to Yarn
    // names at runtime, causing "No refMap loaded" errors.
    mixin { useLegacyMixinAp = true }
}

dependencies {
    // Shared pure-logic module
    api(project(":common"))

    // Minecraft — auto-downloaded by Loom
    "minecraft"(libs.minecraft1201)

    // Yarn mappings for 1.20.1
    "mappings"(libs.yarn1201)

    // Fabric mixin infrastructure (compile-only)
    compileOnlyApi(libs.fabric.loader1201)
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
