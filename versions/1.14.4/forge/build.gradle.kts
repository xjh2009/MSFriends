plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.14.4 entry point"

minecraft {
    mappings("snapshot", "20190601-1.14.2")
    useDefaultAccessTransformer()
}

configurations.configureEach {
    resolutionStrategy {
        force("org.apache.logging.log4j:log4j-api:2.11.2")
        force("org.apache.logging.log4j:log4j-core:2.11.2")
        // Forge 1.14.4 needs SLF4J 1.7.x (2.x needs ServiceProvider which fails in ModLauncher 4)
        force("org.slf4j:slf4j-api:1.7.36")
    }
}

dependencies {
    implementation(project(":common")) {
        exclude(group = "com.mojang", module = "authlib")
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "com.mojang", module = "logging")
        exclude(group = "com.mojang", module = "datafixerupper")
        // Exclude SLF4J 2.x from common (needs ServiceProvider which breaks ModLauncher 4)
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation(minecraft.dependency("net.minecraftforge:forge:1.14.4-28.2.30"))

    val forgeMavenizerRepo = rootProject.layout.projectDirectory.dir(".gradle/mavenizer/repo")
    val forgeJarPath = forgeMavenizerRepo.file("net/minecraftforge/forge/1.14.4-28.2.30/forge-1.14.4-28.2.30.jar")
    if (forgeJarPath.asFile.exists()) {
        implementation(files(forgeJarPath.asFile))
    }

    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("com.mojang:brigadier:1.0.17")
    compileOnly("it.unimi.dsi:fastutil:8.2.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    implementation("org.spongepowered:mixin:0.8.5")
    implementation(libs.webrtc.java)

    // SLF4J is needed at runtime but must NOT conflict with Forge's SLF4J 2.x service descriptors.
    // Bundle 1.7.36 classes only, no service files.
    implementation("org.slf4j:slf4j-api:1.7.36")
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.14.4",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.14.4",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "MOD"
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

    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") || it.name.contains("slf4j-api") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "**/module-info.class", "META-INF/services/**")
    }

    exclude("net/minecraft/**")
}

tasks.named("assemble") { dependsOn("fatJar") }

// Downgrade Java 17 bytecode (61) to Java 8 (52) and strip unsupported attributes
// for Forge 1.14.4 ASM 6.2 compatibility (NestMembers, NestHost, etc.)
tasks.register("downgradeBytecode") {
    dependsOn("fatJar")
    doLast {
        val fatJarTask = tasks.named<Jar>("fatJar").get()
        val jar = fatJarTask.archiveFile.get().asFile
        val script = rootProject.file("tools/downgrade-forge-1144.py")
        val env = HashMap(System.getenv())
        env.remove("JAVA_TOOL_OPTIONS")
        val pb = ProcessBuilder("python", script.absolutePath, jar.absolutePath)
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (output.isNotEmpty()) println(output)
    }
}
tasks.named("assemble") { dependsOn("downgradeBytecode") }

// Reobfuscate MCP names → SRG names for Forge 1.14.4 runtime compatibility.
// Forge 1.14.4 uses SRG names (func_XXXX, field_XXXX) at runtime.
tasks.register("reobfFatJar") {
    dependsOn("downgradeBytecode")
    doLast {
        val fatJarTask = tasks.named<Jar>("fatJar").get()
        val jar = fatJarTask.archiveFile.get().asFile
        val reobfJar = File(jar.parentFile, jar.name.replace("-all.jar", "-all-reobf.jar"))
        val script = rootProject.file("tools/reobf_mcp_to_srg.py")
        val env = HashMap(System.getenv())
        env.remove("JAVA_TOOL_OPTIONS")
        val pb = ProcessBuilder("python", script.absolutePath, jar.absolutePath, reobfJar.absolutePath)
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (output.isNotEmpty()) println(output)
    }
}
tasks.named("assemble") { dependsOn("reobfFatJar") }
