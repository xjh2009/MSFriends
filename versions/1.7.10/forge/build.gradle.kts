plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.7.10 entry point"

minecraft {
    mappings("snapshot", "stable_12")
    useDefaultAccessTransformer()
}

configurations.configureEach {
    resolutionStrategy {
        force("org.apache.logging.log4j:log4j-api:2.11.2")
        force("org.apache.logging.log4j:log4j-core:2.11.2")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10"))

    val forgeMavenizerRepo = rootProject.layout.projectDirectory.dir(".gradle/mavenizer/repo")
    val forgeJar = forgeMavenizerRepo.file("net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10.jar")
    if (forgeJar.asFile.exists()) {
        implementation(files(forgeJar))
    }

    implementation("org.spongepowered:mixin:0.8.5")
    implementation(libs.webrtc.java)
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("mcmod.info") { expand("version" to safeVersion, "mcversion" to "1.7.10") }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.7.10",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.7.10",
    "Implementation-Version" to project.version,
    "FMLCorePlugin" to "dev.msf.friends.asm.MsfFriendsLoadingPlugin",
    "FMLModType" to "GAMELIBRARY",
    "ForceLoadAsMod" to "true"
)

tasks.named<Jar>("jar") {
    manifest { attributes(forgeManifestAttrs) }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a deployable fat jar"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes(forgeManifestAttrs) }
    from(sourceSets.main.get().output)
    val commonJar = project(":common").tasks.named<Jar>("jar")
    dependsOn(commonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") || it.name.contains("slf4j") || it.name.contains("Java-WebSocket") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "**/module-info.class")
    }
    exclude("net/minecraft/**")
}

tasks.named("assemble") { dependsOn("fatJar") }
