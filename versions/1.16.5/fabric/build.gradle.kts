plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF Fabric 1.16.5 entry point"

loom {
}

dependencies {
    implementation(project(":versions:1.16.5:common"))

    "minecraft"(libs.minecraft1165)

    // Yarn mappings for 1.16.5
    "mappings"(libs.yarn1165)

    implementation(libs.fabric.loader1165)
    implementation(libs.fabric.api1165)

    implementation(libs.webrtc.java)
    implementation(libs.slf4j.api)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val commonJar = project(":common").tasks.named<Jar>("jar")
val verCommonJar = project(":versions:1.16.5:common").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    // Bundle webrtc-java and slf4j classes (exclude module-info to avoid JPMS conflicts)
    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") || it.name.contains("slf4j-api") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }
    manifest {
        attributes(
            "Specification-Title" to "MSF-fabric-1.16.5",
            "Specification-Version" to project.version,
            "Implementation-Title" to "MSF-fabric-1.16.5",
            "Implementation-Version" to project.version
        )
    }
}
