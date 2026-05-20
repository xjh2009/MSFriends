plugins {
    id("java-library")
}

group = "dev.msf.core"

description = "MSF common — pure logic, no Minecraft dependency"

dependencies {
    api(libs.jspecify)
    api(libs.slf4j.api)
    api(libs.log4j.api)
    api(libs.gson)
    api(libs.netty.buffer)
    api(libs.netty.transport)
    api(libs.netty.common)
    api(libs.netty.codec.base)
    api(libs.netty.handler)
    api(libs.webrtc.java)
    api(libs.authlib)
    api(libs.brigadier)
    api(libs.logging.mojang)
    api(libs.datafixerupper)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Specification-Title" to "MSF-common",
            "Specification-Version" to project.version,
            "Implementation-Title" to "MSF-common",
            "Implementation-Version" to project.version
        )
    }
}
