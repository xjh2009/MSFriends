plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF 1.17.1 adapter — version-specific code, loader-agnostic"

loom {
    mixin { useLegacyMixinAp = true }
    accessWidenerPath.set(file("src/main/resources/msf-friends.accesswidener"))
}

dependencies {
    api(project(":common"))

    "minecraft"(libs.minecraft1171)
    "mappings"(libs.yarn1171)

    compileOnlyApi(libs.fabric.loader)
    compileOnlyApi(libs.sponge.mixin)

    compileOnlyApi(libs.authlib)
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
