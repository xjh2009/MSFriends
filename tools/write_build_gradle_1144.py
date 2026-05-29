#!/usr/bin/env python3
"""Write 1.14.4 forge build.gradle.kts"""
import os

L = chr(36)  # literal $ for Kotlin string templates

content = f"""\
plugins {{
    alias(libs.plugins.forge.gradle)
}}

description = "MSF Forge 1.14.4 entry point"

minecraft {{
    mappings("snapshot", "20190601-1.14.2")
    useDefaultAccessTransformer()
}}

configurations.configureEach {{
    resolutionStrategy {{
        force("org.apache.logging.log4j:log4j-api:2.11.2")
        force("org.apache.logging.log4j:log4j-core:2.11.2")
    }}
}}

dependencies {{
    implementation(project(":common")) {{
        exclude(group = "com.mojang", module = "authlib")
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "com.mojang", module = "logging")
        exclude(group = "com.mojang", module = "datafixerupper")
    }}

    implementation(minecraft.dependency("net.minecraftforge:forge:1.14.4-28.2.30"))

    val forgeMavenizerRepo = rootProject.layout.projectDirectory.dir(".gradle/mavenizer/repo")
    val forgeJarPath = forgeMavenizerRepo.file("net/minecraftforge/forge/1.14.4-28.2.30/forge-1.14.4-28.2.30.jar")
    if (forgeJarPath.asFile.exists()) {{
        implementation(files(forgeJarPath.asFile))
    }}

    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("com.mojang:brigadier:1.0.17")
    compileOnly("it.unimi.dsi:fastutil:8.2.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    implementation("org.spongepowered:mixin:0.8.5")
    implementation(libs.webrtc.java)
}}

tasks.named<ProcessResources>("processResources") {{
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") {{ expand("version" to safeVersion) }}
}}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.14.4",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.14.4",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "MOD"
)

tasks.named<Jar>("jar") {{
    manifest {{ attributes(forgeManifestAttrs) }}
}}

tasks.register<Jar>("fatJar") {{
    group = "build"
    description = "Assembles a deployable fat jar"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {{ attributes(forgeManifestAttrs) }}
    from(sourceSets.main.get().output)
    val commonJar = project(":common").tasks.named<Jar>("jar")
    dependsOn(commonJar)
    from(commonJar.map {{ zipTree(it.archiveFile) }}) {{ exclude("META-INF/MANIFEST.MF") }}

    from({{ configurations.runtimeClasspath.get().filter {{ it.name.contains("webrtc-java") }}.map {{ zipTree(it) }} }}) {{
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }}

    exclude("net/minecraft/**")
}}

tasks.named("assemble") {{ dependsOn("fatJar") }}

// Downgrade Java 17 bytecode (61) to Java 11 (55) for Forge 1.14.4 ASM 6.2 compatibility.
tasks.register("downgradeBytecode") {{
    dependsOn("fatJar")
    doLast {{
        val jar = layout.buildDirectory.file("libs/{L}{{project.name}}-{L}{{project.version}}-all.jar").get().asFile
        val script = rootProject.file("tools/downgrade-class-version.py")
        val result = providers.exec {{
            commandLine("python", script.absolutePath, jar.absolutePath, "55")
        }}
        val output = result.standardOutput.asText.get().trim()
        if (output.isNotEmpty()) println(output)
    }}
}}
tasks.named("assemble") {{ dependsOn("downgradeBytecode") }}
"""

path = os.path.join(r'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.14.4\forge', 'build.gradle.kts')
with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
print(f'Written {len(content)} chars to {path}')
