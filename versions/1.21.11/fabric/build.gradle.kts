plugins {
    alias(libs.plugins.fabric.loom.remap)
}

description = "MSF Fabric 1.21.11 entry point"

dependencies {
    implementation(project(":versions:1.21.11:common"))

    "minecraft"(libs.mc12111)
    mappings(loom.officialMojangMappings())

    implementation(libs.fabric.loader)
    implementation(libs.fabricApi12111)

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val commonJar = project(":common").tasks.named<Jar>("jar")
val verCommonJar = project(":versions:1.21.11:common").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    manifest {
        attributes(
            "Specification-Title" to "MSF-fabric-1.21.11",
            "Specification-Version" to project.version,
            "Implementation-Title" to "MSF-fabric-1.21.11",
            "Implementation-Version" to project.version
        )
    }
}
