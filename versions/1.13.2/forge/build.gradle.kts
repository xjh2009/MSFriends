plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.13.2 entry point"

minecraft {
    mappings("snapshot", "20180921-1.13")
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

    implementation(minecraft.dependency("net.minecraftforge:forge:1.13.2-25.0.223"))

    // Forge 1.13.2 bundles all modules in a single jar — no separate javafmllanguage/fmlloader/fmlcore.

    // The mavenizer module metadata requires mapping attributes that compileClasspath
    // doesn't provide.  Add the remapped jar as a file dependency so MC classes are
    // on the compile classpath even when the variant resolution fails.
    val forgeMavenizerRepo = rootProject.layout.projectDirectory.dir(".gradle/mavenizer/repo")
    val forgeJar = forgeMavenizerRepo.file("net/minecraftforge/forge/1.13.2-25.0.223/forge-1.13.2-25.0.223.jar")
    if (forgeJar.asFile.exists()) {
        implementation(files(forgeJar))
    }

    // Sponge Mixin — provided at runtime by Forge/FML but needed at compile time
    // for mixin annotations (@Mixin, @Inject, @Shadow, etc.)
    implementation("org.spongepowered:mixin:0.8.5")
    implementation(libs.webrtc.java)

    // SLF4J is used by the common module but NOT provided at runtime by Forge 1.13.2
    implementation("org.slf4j:slf4j-api:2.0.17")
}

tasks.register("showCompileClasspath") {
    doLast {
        configurations.named("compileClasspath").get().files.forEach { f ->
            println("  CP: $f (${f.length()} bytes)")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.13.2",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.13.2",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "MOD"
)

tasks.named<Jar>("jar") {
    manifest { attributes(forgeManifestAttrs) }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a deployable fat jar containing all module classes"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes(forgeManifestAttrs) }
    from(sourceSets.main.get().output)
    val commonJar = project(":common").tasks.named<Jar>("jar")
    dependsOn(commonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }

    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") || it.name.contains("slf4j") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "**/module-info.class")
    }

    // Exclude base Minecraft classes (provided by the MC runtime)
    exclude("net/minecraft/**")
}

tasks.named("assemble") { dependsOn("fatJar") }

// Downgrade Java 17 bytecode (61) to Java 8 (52) and strip unsupported attributes
// for Forge 1.13.2 ASM compatibility (NestMembers, NestHost, etc.)
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

// Reobfuscate MCP names → SRG names for Forge 1.13.2 runtime compatibility.
// Must run AFTER downgradeBytecode to operate on the downgraded jar.
tasks.register("reobfFatJar") {
    dependsOn("downgradeBytecode")
    doLast {
        val fatJarTask = tasks.named<Jar>("fatJar").get()
        val jar = fatJarTask.archiveFile.get().asFile
        val reobfJar = File(jar.parentFile, jar.name.replace("-all.jar", "-all-reobf.jar"))
        val script = rootProject.file("tools/reobf_mcp_to_srg.py")
        val mcpSnapshot = rootProject.layout.projectDirectory.dir(
            ".gradle/mavenizer/repo/net/minecraft/mappings_snapshot" +
            "/1.13.2-20190213.203750-20180921-1.13" +
            "/mappings_snapshot-1.13.2-20190213.203750-20180921-1.13.zip"
        ).asFile
        val tsrg = rootProject.file("tools/mcp-to-srg-1.13.2.tsrg")
        val env = HashMap(System.getenv())
        env.remove("JAVA_TOOL_OPTIONS")
        val pb = ProcessBuilder("python", script.absolutePath, jar.absolutePath, reobfJar.absolutePath,
            mcpSnapshot.absolutePath, tsrg.absolutePath)
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (output.isNotEmpty()) println(output)
    }
}
tasks.named("assemble") { dependsOn("reobfFatJar") }

