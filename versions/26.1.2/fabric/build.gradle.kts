plugins {
    alias(libs.plugins.fabric.loom.noremap)
}

description = "MSF Fabric 26.1.2 entry point"

dependencies {
    implementation(project(":versions:26.1.2:common"))

    "minecraft"(libs.minecraft)

    // MC 26.1 is fully unobfuscated — no mappings needed with no-remap Loom

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val commonJar = project(":common").tasks.named<Jar>("jar")
val verCommonJar = project(":versions:26.1.2:common").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    manifest {
        attributes(
            "Specification-Title" to "MSF-fabric-26.1.2",
            "Specification-Version" to project.version,
            "Implementation-Title" to "MSF-fabric-26.1.2",
            "Implementation-Version" to project.version
        )
    }
}
