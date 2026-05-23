import org.gradle.api.plugins.JavaPluginExtension

plugins {
    base
}

group = "dev.msf"

// Use CI-provided version (e.g. GitHub tag) when available, otherwise fallback
version = System.getenv("CI_VERSION")
    ?.removePrefix("v")
    ?: "0.1.0+26.1.2"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    // Ensure each subproject has a unique artifact name derived from its path
    extensions.configure<org.gradle.api.plugins.BasePluginExtension> {
        archivesName.set(project.path.removePrefix(":").replace(":", "-"))
    }

    // Redirect the entire build directory of every subproject into root build/<subproject-path>
    // e.g. :common -> build/common/, :versions:26.1.2:fabric -> build/versions-26.1.2-fabric/
    layout.buildDirectory.set(
        rootProject.layout.buildDirectory.dir(project.path.removePrefix(":").replace(":", "-"))
    )

    extensions.configure<JavaPluginExtension> {
        val javaVersion = when {
            project.path.contains("1.16.5") || project.path.contains("1.18.2") || project.path.contains("1.19.2") || project.path.contains("1.20.1") || project.path == ":common" -> 17
            project.path.contains("1.21.") -> 21
            else -> 25
        }
        toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    tasks.withType<JavaCompile>().configureEach {
        val javaVersion = when {
            project.path.contains("1.16.5") || project.path.contains("1.18.2") || project.path.contains("1.19.2") || project.path.contains("1.20.1") || project.path == ":common" -> 17
            project.path.contains("1.21.") -> 21
            else -> 25
        }
        options.encoding = "UTF-8"
        options.release = javaVersion
        options.compilerArgs.add("-Xlint:-options")
        // Do NOT enable --enable-preview for projects that run under
        // MC 1.19.2 / 1.20.1 — the JVM won't have preview enabled at
        // runtime, causing UnsupportedClassVersionError (class file
        // version 61.65535). Java 17 sealed/record/switch are stable
        // and don't need preview.
        val needsPreview = when {
            project.path.contains("1.16.5") -> false
            project.path.contains("1.18.2") -> false
            project.path.contains("1.19.2") -> false
            project.path.contains("1.20.1") -> false
            project.path == ":common" -> false   // used by 1.19.2
            else -> true
        }
        if (needsPreview) {
            options.compilerArgs.add("--enable-preview")
        }
    }

    val loomMinecraftMaven = rootProject.layout.projectDirectory
        .dir(".gradle/loom-cache/minecraftMaven").asFile
    val userLoomMaven = java.io.File(System.getProperty("user.home"), ".gradle/caches/fabric-loom/minecraftMaven")

    repositories {
        if (loomMinecraftMaven.exists()) {
            maven { url = loomMinecraftMaven.toURI() }
        }
        if (userLoomMaven.exists()) {
            maven { url = userLoomMaven.toURI() }
        }
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        if (!project.path.contains("1.16.5")) {
            maven { url = uri("https://maven.neoforged.net/releases/") }
        }
    }
}

// ---- Collect final deployable mod jars into root out/ ----
// Only the jars that a mod loader can directly load are copied to the root out/ directory.
// - Fabric: remapped jar (via Loom's remapJar)
// - NeoForge / Forge: relocated fat jar (self-contained, *-all.jar)
tasks.register("collectJars") {
    group = "build"
    description = "Copy all deployable mod jars into root out/"
    dependsOn(
        ":versions:1.21.11:fabric:remapJar",
        //":versions:1.21.11:forge:relocateFatJar",
        //":versions:1.21.11:neoforge:relocateFatJar",
        ":versions:1.21.1:fabric:remapJar",
        ":versions:1.20.1:fabric:remapJar",
        ":versions:1.19.2:fabric:remapJar",
        ":versions:1.18.2:fabric:remapJar",
        ":versions:1.16.5:fabric:remapJar",
        ":versions:26.1.2:fabric:jar"
        //":versions:26.1.2:neoforge:relocateFatJar",
        //":versions:26.1.2:forge:relocateFatJar"
    )

    // Always execute this task — never skip due to UP-TO-DATE checks.
    // Even if the out/ directory already contains jars from a previous build,
    // we must overwrite them with the freshly built artifacts.
    outputs.upToDateWhen { false }

    val outDir = rootProject.layout.projectDirectory.dir("out")

    doLast {
        val versionStr = project.version.toString()
        val modVersion = versionStr.substringBefore("+", versionStr)
        val mcVersion = versionStr.substringAfter("+", libs.versions.minecraft.get())

        outDir.asFile.mkdirs()

        // Fabric 1.21.11
        val fabric12111Jar = project(":versions:1.21.11:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric12111Name = "msfriends-fabric-${modVersion}+1.21.11.jar"
        fabric12111Jar.copyTo(File(outDir.asFile, fabric12111Name), overwrite = true)

        // Forge 1.21.11 — skipped (project not included)
        // val forge12111ArchivesName = project(":versions:1.21.11:forge").path.removePrefix(":").replace(":", "-")
        // val forge12111AllJar = project(":versions:1.21.11:forge").layout.buildDirectory
        //     .file("libs/${forge12111ArchivesName}-${project.version}-all.jar").get().asFile
        // val forge12111Name = "msfriends-forge-${modVersion}+1.21.11.jar"
        // forge12111AllJar.copyTo(File(outDir.asFile, forge12111Name), overwrite = true)

        // NeoForge 1.21.11 — skipped (project not included)
        // val neoforge12111ArchivesName = project(":versions:1.21.11:neoforge").path.removePrefix(":").replace(":", "-")
        // val neoforge12111AllJar = project(":versions:1.21.11:neoforge").layout.buildDirectory
        //     .file("libs/${neoforge12111ArchivesName}-${project.version}-all.jar").get().asFile
        // val neoforge12111Name = "msfriends-neoforge-${modVersion}+1.21.11.jar"
        // neoforge12111AllJar.copyTo(File(outDir.asFile, neoforge12111Name), overwrite = true)

        // Fabric 1.21.1
        val fabric1211Jar = project(":versions:1.21.1:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric1211Name = "msfriends-fabric-${modVersion}+1.21.1.jar"
        fabric1211Jar.copyTo(File(outDir.asFile, fabric1211Name), overwrite = true)

        // Fabric 1.20.1
        val fabric1201Jar = project(":versions:1.20.1:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric1201Name = "msfriends-fabric-${modVersion}+1.20.1.jar"
        fabric1201Jar.copyTo(File(outDir.asFile, fabric1201Name), overwrite = true)

        // Fabric 1.19.2
        val fabric1192Jar = project(":versions:1.19.2:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric1192Name = "msfriends-fabric-${modVersion}+1.19.2.jar"
        fabric1192Jar.copyTo(File(outDir.asFile, fabric1192Name), overwrite = true)

        // Fabric 1.18.2
        val fabric1182Jar = project(":versions:1.18.2:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric1182Name = "msfriends-fabric-${modVersion}+1.18.2.jar"
        fabric1182Jar.copyTo(File(outDir.asFile, fabric1182Name), overwrite = true)

        // Fabric 1.16.5
        val fabric1165Jar = project(":versions:1.16.5:fabric").tasks.named<AbstractArchiveTask>("remapJar").get().archiveFile.get().asFile
        val fabric1165Name = "msfriends-fabric-${modVersion}+1.16.5.jar"
        fabric1165Jar.copyTo(File(outDir.asFile, fabric1165Name), overwrite = true)

        // Fabric 26.1.2
        val fabricJar = project(":versions:26.1.2:fabric").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val fabricName = "msfriends-fabric-${modVersion}+${mcVersion}.jar"
        fabricJar.copyTo(File(outDir.asFile, fabricName), overwrite = true)

        // NeoForge 26.1.2 — skipped (project not included)
        // val neoforge2612ArchivesName = project(":versions:26.1.2:neoforge").path.removePrefix(":").replace(":", "-")
        // val neoforgeAllJar = project(":versions:26.1.2:neoforge").layout.buildDirectory
        //     .file("libs/${neoforge2612ArchivesName}-${project.version}-all.jar").get().asFile
        // val neoforgeName = "msfriends-neoforge-${modVersion}+${mcVersion}.jar"
        // neoforgeAllJar.copyTo(File(outDir.asFile, neoforgeName), overwrite = true)

        // Forge 26.1.2 — skipped (project not included)
        // val forge2612ArchivesName = project(":versions:26.1.2:forge").path.removePrefix(":").replace(":", "-")
        // val forgeAllJar = project(":versions:26.1.2:forge").layout.buildDirectory
        //     .file("libs/${forge2612ArchivesName}-${project.version}-all.jar").get().asFile
        // val forgeName = "msfriends-forge-${modVersion}+${mcVersion}.jar"
        // forgeAllJar.copyTo(File(outDir.asFile, forgeName), overwrite = true)

        logger.lifecycle("Collected jars to out/:")
        outDir.asFile.listFiles()?.filter { it.name.endsWith(".jar") }?.forEach {
            logger.lifecycle("  ${it.name} (${it.length() / 1024} KB)")
        }
    }
}

// Make `gradlew build` also collect the final jars
tasks.named("build") {
    dependsOn("collectJars")
}
