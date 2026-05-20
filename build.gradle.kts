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
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:-options")
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
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
        ":versions:26.1.2:fabric:jar",
        ":versions:26.1.2:neoforge:relocateFatJar",
        ":versions:26.1.2:forge:relocateFatJar"
    )

    val outDir = rootProject.layout.projectDirectory.dir("out")
    outputs.dir(outDir)

    doLast {
        val versionStr = project.version.toString()
        val modVersion = versionStr.substringBefore("+", versionStr)
        val mcVersion = versionStr.substringAfter("+", libs.versions.minecraft.get())

        outDir.asFile.mkdirs()

        // Fabric
        val fabricJar = project(":versions:26.1.2:fabric").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val fabricName = "msfriends-fabric-${modVersion}+${mcVersion}.jar"
        fabricJar.copyTo(File(outDir.asFile, fabricName), overwrite = true)

        // NeoForge
        val neoforgeAllJar = project(":versions:26.1.2:neoforge").layout.buildDirectory
            .file("libs/${project(":versions:26.1.2:neoforge").name}-${project.version}-all.jar").get().asFile
        val neoforgeName = "msfriends-neoforge-${modVersion}+${mcVersion}.jar"
        neoforgeAllJar.copyTo(File(outDir.asFile, neoforgeName), overwrite = true)

        // Forge
        val forgeAllJar = project(":versions:26.1.2:forge").layout.buildDirectory
            .file("libs/${project(":versions:26.1.2:forge").name}-${project.version}-all.jar").get().asFile
        val forgeName = "msfriends-forge-${modVersion}+${mcVersion}.jar"
        forgeAllJar.copyTo(File(outDir.asFile, forgeName), overwrite = true)
    }
}

// Make `gradlew build` also collect the final jars
tasks.named("build") {
    dependsOn("collectJars")
}
