plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF 1.15.2 adapter — version-specific code, loader-agnostic"

loom {
    mixin { useLegacyMixinAp = true }
    accessWidenerPath.set(file("src/main/resources/msf-friends.accesswidener"))
}

dependencies {
    // Exclude authlib 7.x pulled transitively from :common; we need 1.5.x for 1.15.2
    api(project(":common")) {
        exclude(group = "com.mojang", module = "authlib")
    }

    "minecraft"(libs.minecraft1152)
    "mappings"(libs.yarn1152)

    compileOnlyApi(libs.fabric.loader)
    compileOnlyApi(libs.sponge.mixin)

    compileOnlyApi("com.mojang:authlib:1.5.25") // MC 1.15.2 bundles authlib 1.5.x (getId/getName)
    compileOnlyApi(libs.webrtc.java)
    compileOnlyApi(libs.brigadier)
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
