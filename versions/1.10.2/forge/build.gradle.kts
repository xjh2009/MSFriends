// Forge 1.10.2 — compile against cached forgeBin jar (ForgeGradle 7.x doesn't support pre-1.13 Forge)
plugins {
    id("java-library")
}

description = "MSF Forge 1.10.2 entry point"

group = "dev.msf"

repositories {
    mavenCentral()
    maven { url = uri("https://libraries.minecraft.net/") }
    maven { url = uri("https://maven.minecraftforge.net/") }
    maven { url = uri("https://repo.spongepowered.org/maven/") }
}

dependencies {
    // Cached Forge + MC merged jar (MCP mapped, stable_29)
    implementation(files("C:/Users/xjh37/.gradle/caches/minecraft/net/minecraftforge/forge/1.10.2-12.18.3.2511/stable/29/forgeBin-1.10.2-12.18.3.2511.jar"))

    // Common module
    implementation(project(":common")) {
        exclude(group = "com.mojang", module = "authlib")
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "com.mojang", module = "logging")
        exclude(group = "com.mojang", module = "datafixerupper")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Old MC libraries not bundled in forgeBin
    implementation("net.minecraft:launchwrapper:1.12")
    implementation("lzma:lzma:0.0.1")
    implementation("java3d:vecmath:1.5.2")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("com.google.guava:guava:21.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.apache.httpcomponents:httpclient:4.3.3")
    implementation("commons-io:commons-io:2.4")
    implementation("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")
    implementation("org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209")
    implementation("io.netty:netty-all:4.1.17.Final")

    implementation("dev.onvoid.webrtc:webrtc-java:0.14.0")
    implementation("org.spongepowered:mixin:0.8.5")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("com.mojang:authlib:1.5.25")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version, "mcversion" to "1.10.2") }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.10",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.10",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "GAMELIBRARY",
    "tweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
    "FMLCorePlugin" to "dev.msf.friends.asm.MsfFriendsLoadingPlugin",
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
    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") || it.name.contains("slf4j") || it.name.contains("mixin") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "**/module-info.class", "META-INF/services/**")
    }
    exclude("net/minecraft/**")
    exclude("com/mojang/authlib/*.class")
    exclude("com/mojang/authlib/minecraft/**")
    exclude("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService*.class")
    exclude("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService*.class")
    exclude("com/mojang/authlib/yggdrasil/YggdrasilEnvironment.class")
    exclude("com/mojang/authlib/properties/**")
    exclude("com/mojang/authlib/exceptions/**")
}

tasks.named("assemble") { dependsOn("fatJar") }
