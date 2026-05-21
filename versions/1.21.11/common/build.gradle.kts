plugins {
    alias(libs.plugins.fabric.loom.remap)
}

description = "MSF 1.21.11 adapter — version-specific code, loader-agnostic"

dependencies {
    api(project(":common"))

    "minecraft"(libs.mc12111)
    mappings(loom.officialMojangMappings())

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
