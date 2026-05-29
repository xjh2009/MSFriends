import java.net.URI

plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.19.2 entry point"

minecraft {
    useDefaultAccessTransformer()
}

// Pin dynamic versions used by Forge 1.19.2 FML transitive deps to avoid
// failures when maven.neoforged.net is unreachable.
configurations.configureEach {
    resolutionStrategy {
        force("org.apache.logging.log4j:log4j-api:2.17.0")
        force("org.apache.logging.log4j:log4j-core:2.17.0")
        force("cpw.mods:modlauncher:10.0.8")
        force("net.minecraftforge:forgespi:6.0.0")
        force("net.jodah:typetools:0.8.3")
    }
}

dependencies {
    implementation(project(":versions:1.19.2:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.19.2-43.4.0"))

    // Forge splits its runtime into multiple artifacts.  The mavenizer
    // output for the `forge` jar does **not** contain @Mod or FMLPaths, so we
    // must pull the satellite libraries explicitly.
    implementation("net.minecraftforge:javafmllanguage:1.19.2-43.4.0")
    implementation("net.minecraftforge:fmlloader:1.19.2-43.4.0")
    implementation("net.minecraftforge:fmlcore:1.19.2-43.4.0")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    // Forge's Maven version parser rejects '+' in version strings (e.g. "0.1.0+26.1.2").
    // Sanitise by replacing '+' with '-' so it becomes "0.1.0-26.1.2".
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.19.2",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.19.2",
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
    val verCommonJar = project(":versions:1.19.2:common").tasks.named<Jar>("jar")
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }

    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }

    exclude("com/mojang/**")
}

tasks.named("assemble") { dependsOn("relocateFatJar") }

tasks.register("relocateFatJar") {
    group = "build"
    description = "Rebuilds the fat jar with com.mojang classes relocated and Fabric intermediary names stripped"
    dependsOn("fatJar")

    val fatJarTask = tasks.named<Jar>("fatJar")
    val inputJarFile = fatJarTask.flatMap { it.archiveFile }
    // Output to the SAME file as fatJar so it overwrites the unpatched version
    val outputJarFile = inputJarFile

    inputs.file(inputJarFile)
    outputs.file(outputJarFile)

    doLast {
        val srcPkg = "com/mojang/authlib/yggdrasil"
        val dstPkg = "dev/msf/friends/shaded/com/mojang/authlib/yggdrasil"

        val authlibCoreSimpleNames = setOf(
            "YggdrasilAuthenticationService",
            "YggdrasilEnvironment",
            "YggdrasilMinecraftSessionService"
        )

        val jarFile = inputJarFile.get().asFile
        val tmpDir = temporaryDir
        val unpacked = File(tmpDir, "unpacked")
        unpacked.deleteRecursively()
        unpacked.mkdirs()

        ant.withGroovyBuilder {
            "unzip"("src" to jarFile.absolutePath, "dest" to unpacked.absolutePath)
        }

        val commonJarOut = project(":common").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val verCommonJarOut = project(":versions:1.19.2:common").tasks.named<Jar>("jar").get().archiveFile.get().asFile

        val shadedClassInternalNames = mutableSetOf<String>()

        for (depJar in listOf(commonJarOut, verCommonJarOut)) {
            val depTmp = File(tmpDir, "dep_${depJar.name}")
            depTmp.mkdirs()
            ant.withGroovyBuilder {
                "unzip"("src" to depJar.absolutePath, "dest" to depTmp.absolutePath)
            }
            val depSrcDir = File(depTmp, srcPkg)
            val dstDir = File(unpacked, dstPkg)
            if (depSrcDir.exists()) {
                dstDir.parentFile.mkdirs()
                depSrcDir.copyRecursively(dstDir, overwrite = true)
                depSrcDir.walkTopDown()
                    .filter { it.name.endsWith(".class") }
                    .forEach { classFile ->
                        val relPath = depSrcDir.toPath().relativize(classFile.toPath()).toString()
                        val internalName = relPath.removeSuffix(".class").replace('\\', '/')
                        shadedClassInternalNames.add(internalName)
                    }
            }
        }

        logger.lifecycle("Shaded classes: $shadedClassInternalNames")

        val replacements = mutableMapOf<String, String>()

        for (internalName in shadedClassInternalNames) {
            val simpleName = internalName.substringAfterLast('/')
            val baseName = simpleName.substringBefore('$')
            if (baseName in authlibCoreSimpleNames) continue

            replacements["$srcPkg/$internalName"] = "$dstPkg/$internalName"
            val oldDot = "$srcPkg/$internalName".replace('/', '.')
            val newDot = "$dstPkg/$internalName".replace('/', '.')
            replacements[oldDot] = newDot
        }

        logger.lifecycle("Authlib replacements (${replacements.size}):")
        replacements.forEach { (k, v) -> logger.lifecycle("  $k -> $v") }

        // === SRG remapping for Forge 1.19.2 ===
        val refmapFile = File(unpacked, "versions-1.19.2-common-refmap.json")
        val mixinJsonFile = File(unpacked, "msf-friends.mixins.json")

        logger.lifecycle("Looking for refmap at: ${refmapFile.absolutePath}  exists=${refmapFile.exists()}")
        logger.lifecycle("Looking for mixin JSON at: ${mixinJsonFile.absolutePath}  exists=${mixinJsonFile.exists()}")

        // Forge 1.19.2 runtime uses SRG naming (not Yarn/intermediary).
        // We bridge: Yarn→obf→SRG and intermediary→obf→SRG using both mapping files.

        // --- Locate mapping files ---
        val loomCacheDir = File(project.gradle.gradleUserHomeDir, "caches/fabric-loom/1.19.2")
        val layeredDir = loomCacheDir.listFiles()
            ?.filter { it.name.startsWith("loom.mappings.1_19_2.layered") }
            ?.maxByOrNull { it.name }
        val yarnMappingFile = if (layeredDir != null) File(layeredDir, "mappings-base.tiny") else null
        val effectiveYarnFile = if (yarnMappingFile != null && yarnMappingFile.exists()) yarnMappingFile else {
            val yarnVersion = libs.versions.yarn1192.get()
            val fallbackV2 = File(loomCacheDir, "net.fabricmc.yarn.1_19_2.${yarnVersion}-v2/mappings-base.tiny")
            val fallbackPlain = File(loomCacheDir, "net.fabricmc.yarn.1_19_2.${yarnVersion}/mappings-base.tiny")
            if (fallbackV2.exists()) fallbackV2 else fallbackPlain
        }

        val srgMappingFile = File(
            project.gradle.gradleUserHomeDir,
            "caches/minecraftforge/forgegradle/mavenizer/caches/mcp/de/oceanlabs/mcp/mcp_config" +
                "/1.19.2-20220805.130853/client/data/mappings/joined.tsrg"
        )

        logger.lifecycle("Yarn mapping file: $effectiveYarnFile (exists=${effectiveYarnFile.exists()})")
        logger.lifecycle("SRG mapping file: $srgMappingFile (exists=${srgMappingFile.exists()})")

        // Build a set of Mojang class names for constant-pool-aware bare-name replacement
        var mcClassInternalNames = emptySet<String>()

        // Declare mapping maps at this scope so refmap conversion code can access them
        val srgClassByObf = mutableMapOf<String, String>()
        val srgMethodByKey = mutableMapOf<String, String>()
        val srgFieldByKey = mutableMapOf<String, String>()
        val mojangClassByObf = mutableMapOf<String, String>()
        val obfToYarnClass = mutableMapOf<String, String>()
        val obfToIntClass = mutableMapOf<String, String>()
        val methodObfKeyToYarn = mutableMapOf<String, String>()
        val methodObfKeyToInt = mutableMapOf<String, String>()
        val fieldObfKeyToYarn = mutableMapOf<String, String>()
        val fieldObfKeyToInt = mutableMapOf<String, String>()

        if (effectiveYarnFile.exists() && srgMappingFile.exists()) {
            // Step 1: Parse joined.tsrg → obf→SRG maps
            // tsrg2 format: header "tsrg2 obf srg id"
            //   Class: obf\tSRG_class\tid
            //   Field member: \tobf_field\tsrg_field\tid
            //   Method member: \tobf_method\tdesc\tsrg_method\tid
            var currentObf = ""

            srgMappingFile.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("tsrg2")) continue
                    if (line.startsWith("\t")) {
                        val memberParts = line.trimStart('\t').split("\\s+".toRegex())
                        if (memberParts.size >= 3) {
                            val obfMember = memberParts[0]
                            if (obfMember == "<init>" || obfMember == "<clinit>") continue
                            if (memberParts[1].startsWith("(")) {
                                // Method: obf desc srg [id]
                                val desc = memberParts[1]
                                val srgName = memberParts[2]
                                srgMethodByKey["$currentObf|$desc|$obfMember"] = srgName
                            } else {
                                // Field: obf srg [id]
                                val srgName = memberParts[1]
                                srgFieldByKey["$currentObf|$obfMember"] = srgName
                            }
                        }
                    } else {
                        val classParts = line.split("\\s+".toRegex())
                        if (classParts.size >= 2) {
                            currentObf = classParts[0]
                            srgClassByObf[currentObf] = classParts[1]
                        }
                    }
                }
            }
            logger.lifecycle("Parsed SRG: ${srgClassByObf.size} classes, ${srgMethodByKey.size} methods, ${srgFieldByKey.size} fields")

            // Step 1b: Parse Mojang ProGuard mapping → obf→Mojang class names
            // Forge 1.19.2 runtime uses Mojang class names (e.g. net/minecraft/client/Minecraft)
            // NOT SRG C_XXXXX_ names. ProGuard format: "com.mojang.blaze3d.Blaze3D -> dyh:"
            val mojangMappingFile = project.rootProject.file("build/mojang-client.txt")
            if (!mojangMappingFile.exists()) {
                logger.lifecycle("Downloading Mojang ProGuard mapping...")
                val url = URI("https://piston-data.mojang.com/v1/objects/8e8c9be5dc27802caba47053d4fdea328f7f89bd/client.txt").toURL()
                mojangMappingFile.parentFile.mkdirs()
                url.openStream().use { input ->
                    mojangMappingFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (mojangMappingFile.exists()) {
                mojangMappingFile.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith(" ") || line.startsWith("#")) continue
                        val arrow = line.indexOf(" -> ")
                        if (arrow < 0) continue
                        val mojangFqcn = line.substring(0, arrow).trim()
                        val obfPart = line.substring(arrow + 4).trimEnd(':')
                        // Convert dots to slashes for internal name
                        val mojangInternal = mojangFqcn.replace('.', '/')
                        val obfInternal = obfPart.replace('.', '/')
                        mojangClassByObf[obfInternal] = mojangInternal
                    }
                }
                logger.lifecycle("Parsed Mojang ProGuard: ${mojangClassByObf.size} classes")
            } else {
                logger.lifecycle("WARNING: Mojang ProGuard mapping not found at ${mojangMappingFile.absolutePath}")
            }

            // Step 2: Parse mappings-base.tiny → obf→Yarn and obf→intermediary
            // v1 tiny: header "v1  official  intermediary  named"
            //   CLASS \t obf \t intermediary \t named
            //   METHOD \t obfOwner \t desc \t obfMethod \t intermediary \t named
            //   FIELD \t obfOwner \t desc \t obfField \t intermediary \t named
            var isV1 = false

            // Detect format
            effectiveYarnFile.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("v1")) { isV1 = true; break }
                    if (trimmed.startsWith("v2")) { isV1 = false; break }
                }
            }
            logger.lifecycle("Yarn mapping format: ${if (isV1) "v1 tiny" else "v2 tiny"}")

            effectiveYarnFile.useLines { lines ->
                for (line in lines) {
                    val parts = line.trimStart().split("\t")
                    if (parts.isEmpty()) continue
                    if (isV1) {
                        when {
                            parts[0] == "CLASS" && parts.size >= 4 -> {
                                obfToYarnClass[parts[1]] = parts[3]
                                obfToIntClass[parts[1]] = parts[2]
                            }
                            parts[0] == "METHOD" && parts.size >= 6 -> {
                                val key = "${parts[1]}|${parts[2]}|${parts[3]}"
                                methodObfKeyToYarn[key] = parts[5]
                                methodObfKeyToInt[key] = parts[4]
                            }
                            parts[0] == "FIELD" && parts.size >= 6 -> {
                                val key = "${parts[1]}|${parts[3]}"
                                fieldObfKeyToYarn[key] = parts[5]
                                fieldObfKeyToInt[key] = parts[4]
                            }
                        }
                    } else {
                        // v2 format
                        when {
                            parts[0] == "c" && parts.size >= 3 -> {
                                obfToYarnClass[parts[1]] = parts[2]
                                obfToIntClass[parts[1]] = parts[1]
                            }
                            parts[0] == "m" && parts.size >= 4 -> {
                                // v2: m \t desc \t obf \t intermediary [\t named]
                                // Note: in v2 the "owner" is implicit (current class)
                                // This format needs special handling — skip for now, v1 is the common case
                            }
                            parts[0] == "f" && parts.size >= 4 -> {
                                // Similar to v2 methods
                            }
                        }
                    }
                }
            }
            logger.lifecycle("Parsed Yarn: ${obfToYarnClass.size} classes, ${methodObfKeyToYarn.size} methods, ${fieldObfKeyToYarn.size} fields")

            // Step 3: Bridge through obf to build Yarn→Mojang and intermediary→Mojang class maps
            // Forge 1.19.2 runtime uses Mojang class names, NOT SRG C_XXXXX_ intermediate names
            // Class mappings use Mojang names from ProGuard mapping
            for ((obf, yarnClass) in obfToYarnClass) {
                val mojangClass = mojangClassByObf[obf] ?: continue
                replacements[yarnClass] = mojangClass
                replacements[yarnClass.replace('/', '.')] = mojangClass.replace('/', '.')
            }
            for ((obf, intClass) in obfToIntClass) {
                val mojangClass = mojangClassByObf[obf] ?: continue
                replacements[intClass] = mojangClass
                replacements[intClass.replace('/', '.')] = mojangClass.replace('/', '.')
            }

            // Method mappings: intermediary→SRG and Yarn→SRG
            // Fabric Loom compiles bytecode with Yarn names for MC methods.
            // We add both intermediary and Yarn mappings, filtering out short
            // generic names (length < 6) that would cause false positives.
            val yarnMethodToSrgMultiMap = mutableMapOf<String, MutableSet<String>>()
            for ((obfKey, intMethod) in methodObfKeyToInt) {
                val srgMethod = srgMethodByKey[obfKey] ?: continue
                if (intMethod != srgMethod) {
                    replacements[intMethod] = srgMethod
                }
                val yarnMethod = methodObfKeyToYarn[obfKey] ?: continue
                if (yarnMethod != srgMethod && yarnMethod.length >= 6) {
                    yarnMethodToSrgMultiMap.getOrPut(yarnMethod) { mutableSetOf() }.add(srgMethod)
                }
            }
            // Add common multi-overload methods that couldn't be auto-mapped
            // because they map to multiple SRG names. We pick the most common overload.
            replacements["translatable"] = "m_237113_"

            for ((yarnName, srgNames) in yarnMethodToSrgMultiMap) {
                if (srgNames.size == 1) {
                    replacements[yarnName] = srgNames.first()
                }
                // For multi-overload methods, add descriptor-qualified entries
                // so path-qualified replacement can handle them.
                // Key: "yarnName(descriptor)" Value: "srgName(descriptor)"
                if (srgNames.size > 1) {
                    for ((obfKey, yarnMethod) in methodObfKeyToYarn) {
                        if (yarnMethod != yarnName) continue
                        val srgMethod = srgMethodByKey[obfKey] ?: continue
                        if (yarnMethod == srgMethod) continue
                        // obfKey format: "owner|desc|name"
                        val parts = obfKey.split("|")
                        if (parts.size >= 3) {
                            val desc = parts[1]
                            replacements["$yarnName$desc"] = "$srgMethod$desc"
                        }
                    }
                }
            }

            // Field mappings: intermediary→SRG and Yarn→SRG
            val yarnFieldToSrgMultiMap = mutableMapOf<String, MutableSet<String>>()
            for ((obfKey, intField) in fieldObfKeyToInt) {
                val srgField = srgFieldByKey[obfKey] ?: continue
                if (intField != srgField) {
                    replacements[intField] = srgField
                }
                val yarnField = fieldObfKeyToYarn[obfKey] ?: continue
                if (yarnField != srgField && yarnField.length >= 6) {
                    yarnFieldToSrgMultiMap.getOrPut(yarnField) { mutableSetOf() }.add(srgField)
                }
            }
            for ((yarnName, srgNames) in yarnFieldToSrgMultiMap) {
                if (srgNames.size == 1) {
                    replacements[yarnName] = srgNames.first()
                }
            }

            logger.lifecycle("SRG remapping table: ${replacements.size} total entries (Mojang classes + SRG methods/fields + authlib)")

            // Build a set of original class names known to be Minecraft classes
            // (for constant-pool-aware bare-name replacement).
            // Include BOTH original (Yarn/intermediary) AND target (Mojang) class names,
            // because path-qualified replacement changes class names in the constant pool
            // before bare-name replacement runs, so both forms may appear.
            val originalClassNames = replacements.keys.filter { 
                it.contains('/') && !it.contains('.') && !it.startsWith("dev/")
            }.map { key ->
                if (key.startsWith("L") && key.endsWith(";")) key.substring(1, key.length - 1) else key
            }
            val targetClassNames = replacements.values.filter {
                it.contains('/') && !it.contains('.') && !it.startsWith("dev/")
            }.map { value ->
                if (value.startsWith("L") && value.endsWith(";")) value.substring(1, value.length - 1) else value
            }
            mcClassInternalNames = (originalClassNames + targetClassNames).toSet()
        } else {
            logger.lifecycle("WARNING: Could not find mapping files — SRG remapping will NOT be applied!")
        }

        // Split replacements into path-qualified (safe for all entries) and bare names
        // Path-qualified entries contain '/' or '.' and represent qualified class names.
        // Bare names are plain identifiers (method/field names) — only applied as exact
        // matches to standalone constants in classes that reference MC types.
        val pathQualified = replacements.entries.filter {
            it.key.contains('/') || it.key.contains('.')
        }.sortedByDescending { it.key.length }
        val bareNames = replacements.entries.filter {
            !it.key.contains('/') && !it.key.contains('.')
        }.sortedByDescending { it.key.length }

        logger.lifecycle("SRG remapping: ${pathQualified.size} path-qualified, ${bareNames.size} bare-name entries")

        // Convert Fabric refmap (Yarn→intermediary) to Forge format (Yarn→SRG+Mojang).
        // The refmap tells Mixin which method/field to target by name.
        // Fabric refmap maps: "tick" → "Lnet/minecraft/class_310;method_1574()V" (intermediary)
        // Forge refmap maps:  "tick" → "Lnet/minecraft/client/Minecraft;m_5705_()V" (SRG+Mojang)
        if (refmapFile.exists()) {
            val refmapText = refmapFile.readText()
            
            // Build intermediary→Mojang class lookup
            // "Lnet/minecraft/class_310;" → "Lnet/minecraft/client/Minecraft;"
            val intClassToMojang = mutableMapOf<String, String>()
            logger.lifecycle("Refmap: obfToIntClass.size=${obfToIntClass.size}, mojangClassByObf.size=${mojangClassByObf.size}")
            for ((obf, intClass) in obfToIntClass) {
                val mojang = mojangClassByObf[obf] ?: continue
                intClassToMojang["L$intClass;"] = "L$mojang;"
            }
            logger.lifecycle("Refmap: intClassToMojang.size=${intClassToMojang.size}")
            if (intClassToMojang.isNotEmpty()) {
                val sample = intClassToMojang.entries.take(3)
                for ((k, v) in sample) logger.lifecycle("  intClassToMojang: $k -> $v")
            }
            
            // Build intermediary→SRG method lookup (keyed by obfKey for disambiguation)
            // We map: intermediary method name → SRG method name, using obf as bridge
            // But since multiple obf methods can map to the same intermediary, we need
            // the obf→intermediary map and obf→SRG map, keyed by owner+desc.
            // For the refmap, intermediary names appear as "method_XXXXX" in values.
            // We build a simple map: intermediary name → SRG name (unique mappings only)
            val intMethodToSrg = mutableMapOf<String, String>()
            val intMethodToSrgMulti = mutableMapOf<String, MutableSet<String>>()
            for ((obfKey, intMethod) in methodObfKeyToInt) {
                val srg = srgMethodByKey[obfKey] ?: continue
                if (intMethod != srg) {
                    intMethodToSrgMulti.getOrPut(intMethod) { mutableSetOf() }.add(srg)
                }
            }
            for ((intName, srgNames) in intMethodToSrgMulti) {
                if (srgNames.size == 1) intMethodToSrg[intName] = srgNames.first()
            }
            logger.lifecycle("Refmap: intMethodToSrg.size=${intMethodToSrg.size}")
            if (intMethodToSrg.isNotEmpty()) {
                val sample = intMethodToSrg.entries.take(3)
                for ((k, v) in sample) logger.lifecycle("  intMethodToSrg: $k -> $v")
            }
            
            val intFieldToSrg = mutableMapOf<String, String>()
            val intFieldToSrgMulti = mutableMapOf<String, MutableSet<String>>()
            for ((obfKey, intField) in fieldObfKeyToInt) {
                val srg = srgFieldByKey[obfKey] ?: continue
                if (intField != srg) {
                    intFieldToSrgMulti.getOrPut(intField) { mutableSetOf() }.add(srg)
                }
            }
            for ((intName, srgNames) in intFieldToSrgMulti) {
                if (srgNames.size == 1) intFieldToSrg[intName] = srgNames.first()
            }
            
            // Transform refmap values: each value is either a method descriptor or field reference.
            // Method: "Lnet/minecraft/class_310;method_1574()V"
            // Field:  "field_1746:Lnet/minecraft/class_2535;"
            fun transformRefmapValue(value: String): String {
                val semiIdx = value.indexOf(';')
                if (semiIdx < 0) return value
                
                val afterSemi = value.substring(semiIdx + 1)
                if (afterSemi.startsWith("field_") || afterSemi.startsWith("method_")) {
                    // Method format: "Lclass;method_NNNN(desc)ret"  →  convert class and method
                    // Field format: "Lclass;field_NNNN:desc"  →  won't match (field comes before ;)
                    // Actually method/field names come after the semicolon in methods,
                    // and before the colon in fields. Let me handle both.
                    
                    // Check if this is a method: class;methodName(desc)
                    val parenIdx = afterSemi.indexOf('(')
                    if (parenIdx > 0) {
                        val methodName = afterSemi.substring(0, parenIdx)
                        val srgMethod = intMethodToSrg[methodName]
                        if (srgMethod != null) {
                            // Also transform the class part
                            var classPart = value.substring(0, semiIdx + 1)
                            for ((intRef, mojangRef) in intClassToMojang) {
                                classPart = classPart.replace(intRef, mojangRef)
                            }
                            // Also transform class references in the descriptor
                            val descPart = afterSemi.substring(parenIdx)
                            var transformedDesc = descPart
                            for ((intRef, mojangRef) in intClassToMojang) {
                                transformedDesc = transformedDesc.replace(intRef, mojangRef)
                            }
                            return "$classPart$srgMethod$transformedDesc"
                        }
                    }
                    
                    // Transform class part anyway
                    var result = value
                    for ((intRef, mojangRef) in intClassToMojang) {
                        result = result.replace(intRef, mojangRef)
                    }
                    return result
                } else {
                    // Might be field format: "field_NNNN:Lclass;" (field name before semicolon)
                    val colonIdx = value.indexOf(':')
                    if (colonIdx in 0 until semiIdx) {
                        val fieldName = value.substring(0, colonIdx)
                        val srgField = intFieldToSrg[fieldName]
                        if (srgField != null) {
                            var classPart = value.substring(colonIdx)
                            for ((intRef, mojangRef) in intClassToMojang) {
                                classPart = classPart.replace(intRef, mojangRef)
                            }
                            return "$srgField$classPart"
                        }
                    }
                    
                    // Just transform class references
                    var result = value
                    for ((intRef, mojangRef) in intClassToMojang) {
                        result = result.replace(intRef, mojangRef)
                    }
                    return result
                }
            }
            
            // Process refmap JSON by finding all value strings and transforming them.
            // The refmap format has values like: "tick": "Lnet/minecraft/class_310;method_1574()V"
            // We use regex to find all JSON string values (after ": ") and transform them.
            var convertedText = refmapText.replace(Regex("\"([^\"]+)\"")) { match ->
                val str = match.groupValues[1]
                // Only transform strings that look like descriptors (contain ; and class references)
                if (str.contains(";") && (str.contains("class_") || str.contains("field_") || str.contains("method_"))) {
                    "\"${transformRefmapValue(str)}\""
                } else {
                    match.value  // Keep keys and non-descriptor values unchanged
                }
            }
            
            refmapFile.writeText(convertedText)
            logger.lifecycle("Converted Fabric refmap to Forge format (Yarn→SRG+Mojang)")
            
            // Second pass: convert Yarn class names in refmap KEYS to Mojang.
            // Forge mixin remaps descriptor classes before looking up the refmap,
            // so keys must use Mojang names (e.g. PoseStack not MatrixStack).
            val yarnToMojangClass = mutableMapOf<String, String>()
            for ((obf, yarnClass) in obfToYarnClass) {
                val mojangClass = mojangClassByObf[obf] ?: continue
                if (yarnClass != mojangClass) {
                    yarnToMojangClass["L$yarnClass;"] = "L$mojangClass;"
                }
            }
            for ((yarnRef, mojangRef) in yarnToMojangClass) {
                convertedText = convertedText.replace(yarnRef, mojangRef)
            }
            // Also convert intermediary class names in refmap keys
            for ((intRef, mojangRef) in intClassToMojang) {
                convertedText = convertedText.replace(intRef, mojangRef)
            }
            refmapFile.writeText(convertedText)
            
            // Add missing refmap entries for @Shadow fields the AP didn't generate.
            var refmapText2 = refmapFile.readText()
            val missingEntries = mutableMapOf<String, String>()
            if (!refmapText2.contains("OptionsMixin")) {
                missingEntries["dev/msf/friends/mixin/OptionsMixin"] = "{\"allKeys\": \"f_92059_:[Lnet/minecraft/client/KeyMapping;\"}"
            }
            if (!refmapText2.contains("f_96543_")) {
                // Merge width/height into existing TitleScreenMixin entry
                // Use regex to match regardless of whitespace/newlines
                refmapText2 = refmapText2.replace(
                    Regex("\"TitleScreenMixin\": \\{\\s*"),
                    "\"TitleScreenMixin\": {\"width\": \"f_96543_:I\", \"height\": \"f_96544_:I\", "
                )
                logger.lifecycle("Added width/height to TitleScreenMixin refmap entry")
            }
            if (missingEntries.isNotEmpty()) {
                var insertText = ""
                for ((cls, entry) in missingEntries) {
                    insertText += "    \"$cls\": $entry," + "\n"
                }
                refmapText2 = refmapText2.replace(
                    "\"mappings\": {",
                    "\"mappings\": {\n" + insertText
                )
                for ((cls, _) in missingEntries) logger.lifecycle("Added missing refmap entry for $cls")
            }
            refmapFile.writeText(refmapText2)
        } else {
            logger.lifecycle("WARNING: No refmap found at ${refmapFile.absolutePath}")
        }

        // Apply SRG replacements to ALL class files.
        // The bare-name safety check ensures only names whose MethodRef/FieldRef
        // owners are all MC classes get replaced, protecting @Shadow fields in mixins.
        var patchedCount = 0
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            // Mixin classes: skip bare-name replacement to let mixin transformer
            // handle method/field remapping via refmap at runtime.
            val isMixin = classFile.absolutePath.contains("mixin")
            val effectiveBare = if (isMixin) emptyList() else bareNames
            // Pass explicitly added overloaded method names (like "translatable") that
            // bypass the MC-class safety check in the constant pool patcher.
            val explicitBareNames = setOf("translatable")
            val patched = patchClassConstantPool(original, pathQualified, effectiveBare, mcClassInternalNames, explicitBareNames)
            if (!patched.contentEquals(original)) {
                classFile.writeBytes(patched)
                patchedCount++
            }
        }
        logger.lifecycle("Patched $patchedCount class files with SRG names")

        val outputFile = outputJarFile.get().asFile
        outputFile.parentFile.mkdirs()
        ant.withGroovyBuilder {
            "jar"("destfile" to outputFile.absolutePath, "basedir" to unpacked.absolutePath, "manifest" to File(unpacked, "META-INF/MANIFEST.MF").absolutePath)
        }
        logger.lifecycle("Relocated fat jar written to: $outputFile")
    }
}

fun patchClassConstantPool(
    classBytes: ByteArray,
    pathQualified: List<Map.Entry<String, String>>,
    bareNames: List<Map.Entry<String, String>>,
    mcClassNames: Set<String> = emptySet(),
    explicitNames: Set<String> = emptySet()
): ByteArray {
    if (classBytes.size < 10) return classBytes

    var pos = 0
    pos += 4   // magic
    pos += 2   // minor
    pos += 2   // major

    val cpCount = readU2(classBytes, pos)
    pos += 2

    data class Utf8Entry(
        val cpIndex: Int,
        val lengthFieldPos: Int,
        val dataPos: Int,
        val length: Int,
        val value: String
    )

    // For constant-pool-aware Phase 2: track which Utf8 entries are method/field names
    // belonging exclusively to MC classes.
    // CP entry tracking
    val utf8Indices = mutableMapOf<Int, Int>()           // cpIndex → utf8Entries list index
    val utf8Entries = mutableListOf<Utf8Entry>()

    // classRefs: cpIndex → nameUtf8Index (tag 7 = Class)
    val classRefs = mutableMapOf<Int, Int>()
    // nameAndTypeRefs: cpIndex → Pair(nameUtf8Index, descUtf8Index) (tag 12)
    val nameAndTypeRefs = mutableMapOf<Int, Pair<Int, Int>>()
    // methodFieldRefs: cpIndex → Pair(classIndex, natIndex) for tags 9,10,11
    val methodFieldRefs = mutableMapOf<Int, Pair<Int, Int>>()

    var anyPathMatch = false
    var anyBareMatch = false

    // First pass: collect all constant pool entries
    var i = 1
    var scanPos = pos
    while (i < cpCount && scanPos < classBytes.size) {
        val tag = classBytes[scanPos].toInt() and 0xFF
        scanPos += 1

        when (tag) {
            1 -> { // Utf8
                val lengthFieldPos = scanPos
                val length = readU2(classBytes, scanPos)
                scanPos += 2
                val dataPos = scanPos
                if (scanPos + length > classBytes.size) break
                val utf8Str = String(classBytes.copyOfRange(scanPos, scanPos + length), Charsets.UTF_8)
                utf8Indices[i] = utf8Entries.size
                utf8Entries.add(Utf8Entry(i, lengthFieldPos, dataPos, length, utf8Str))

                if (!anyPathMatch) {
                    for ((old, _) in pathQualified) {
                        if (utf8Str.contains(old)) { anyPathMatch = true; break }
                    }
                }
                if (!anyBareMatch) {
                    for ((old, _) in bareNames) {
                        if (utf8Str.contains(old)) { anyBareMatch = true; break }
                    }
                }
                scanPos += length
            }
            3 -> { scanPos += 4 }  // Integer
            4 -> { scanPos += 4 }  // Float
            5 -> { scanPos += 8; i++ }  // Long (takes 2 slots)
            6 -> { scanPos += 8; i++ }  // Double
            7 -> { // Class
                val nameIdx = readU2(classBytes, scanPos)
                classRefs[i] = nameIdx
                scanPos += 2
            }
            8 -> { scanPos += 2 }  // String
            9 -> { // Fieldref
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            10 -> { // Methodref
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            11 -> { // InterfaceMethodref
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            12 -> { // NameAndType
                val nameIdx = readU2(classBytes, scanPos)
                val descIdx = readU2(classBytes, scanPos + 2)
                nameAndTypeRefs[i] = Pair(nameIdx, descIdx)
                scanPos += 4
            }
            15 -> { scanPos += 3 }  // MethodHandle
            16 -> { scanPos += 2 }  // MethodType
            17 -> { scanPos += 4 }  // Dynamic
            18 -> { scanPos += 4 }  // InvokeDynamic
            19 -> { scanPos += 2 }  // Module
            20 -> { scanPos += 2 }  // Package
            else -> break
        }
        i++
    }

    if (!anyPathMatch && !anyBareMatch) return classBytes

    // Quick exit: if no path-qualified matches in this class, it doesn't reference
    // any Minecraft classes — skip bare-name matching entirely to avoid false positives.
    if (!anyPathMatch) return classBytes

    // Build a set of "safe" bare-name Utf8 entry positions for Phase 2.
    // A Utf8 entry is safe for bare-name replacement only if ALL MethodRef/FieldRef/
    // InterfaceMethodRef entries that reference it (via NameAndType) point to MC classes.
    // This prevents corrupting calls to our own mod's methods.
    val safeBareNameUtf8Positions = mutableSetOf<Int>()

    if (bareNames.isNotEmpty()) {
        // Build set of bare-name Utf8 values for quick lookup
        val bareNameValues = bareNames.map { it.key }.toSet()

        // For each Utf8 entry that matches a bare name, check if it's safe to replace
        for ((listIdx, entry) in utf8Entries.withIndex()) {
            if (entry.value !in bareNameValues) continue
            val utf8CpIndex = entry.cpIndex

            // Find all NameAndType entries that use this Utf8 as their name
            val referencingNATs = nameAndTypeRefs.filter { (_, nameDesc) -> nameDesc.first == utf8CpIndex }
            if (referencingNATs.isEmpty()) continue

            // For each such NameAndType, find all MethodRef/FieldRef/InterfaceMethodRef entries
            var allRefsAreMC = true
            var hasAnyRef = false

            for ((natCpIndex, _) in referencingNATs) {
                val refs = methodFieldRefs.filter { (_, classNat) -> classNat.second == natCpIndex }
                for ((_, classNatPair) in refs) {
                    hasAnyRef = true
                    val classCpIndex = classNatPair.first
                    val classNameUtf8Index = classRefs[classCpIndex]
                    if (classNameUtf8Index != null) {
                        val classNameListIdx = utf8Indices[classNameUtf8Index]
                        if (classNameListIdx != null) {
                            val className = utf8Entries[classNameListIdx].value
                            if (className !in mcClassNames) {
                                allRefsAreMC = false
                                break
                            }
                        } else {
                            allRefsAreMC = false
                            break
                        }
                    } else {
                        allRefsAreMC = false
                        break
                    }
                }
                if (!allRefsAreMC) break
            }

            if (hasAnyRef && (allRefsAreMC || entry.value in explicitNames)) {
                safeBareNameUtf8Positions.add(entry.dataPos)
            }
        }
    }

    val newEntries = mutableMapOf<Int, Pair<String, ByteArray>>()
    var totalSizeDelta = 0

    for (entry in utf8Entries) {
        val original = entry.value
        data class ReplacementRange(val start: Int, val end: Int, val replacement: String)
        val ranges = mutableListOf<ReplacementRange>()

        // Phase 1: path-qualified replacements (substring match — safe for class paths)
        for ((old, new_) in pathQualified) {
            var searchFrom = 0
            while (true) {
                val idx = original.indexOf(old, searchFrom)
                if (idx < 0) break
                val overlaps = ranges.any { r -> idx < r.end && (idx + old.length) > r.start }
                if (!overlaps) {
                    ranges.add(ReplacementRange(idx, idx + old.length, new_))
                }
                searchFrom = idx + 1
            }
        }

        // Phase 2: bare name replacements — only applied to Utf8 entries that are
        // verified to be method/field names belonging exclusively to MC classes.
        // This prevents corrupting our own mod's method/field names.
        if (ranges.isEmpty() && entry.dataPos in safeBareNameUtf8Positions) {
            for ((old, new_) in bareNames) {
                if (original == old) {
                    ranges.add(ReplacementRange(0, old.length, new_))
                    break
                }
            }
        }

        if (ranges.isEmpty()) continue

        ranges.sortBy { it.start }

        val sb = StringBuilder()
        var lastEnd = 0
        for (range in ranges) {
            if (range.start > lastEnd) {
                sb.append(original.substring(lastEnd, range.start))
            }
            sb.append(range.replacement)
            lastEnd = range.end
        }
        if (lastEnd < original.length) {
            sb.append(original.substring(lastEnd))
        }

        val newStr = sb.toString()
        val newBytes = newStr.toByteArray(Charsets.UTF_8)
        newEntries[entry.dataPos] = Pair(newStr, newBytes)

        val oldBytesLen = entry.length
        val newBytesLen = newBytes.size
        totalSizeDelta += (newBytesLen - oldBytesLen)
    }

    if (newEntries.isEmpty()) return classBytes

    val result = ByteArray(classBytes.size + totalSizeDelta)
    var srcPos = 0
    var dstPos = 0

    for (entry in utf8Entries) {
        val copyUpTo = entry.lengthFieldPos
        val copyLen = copyUpTo - srcPos
        if (copyLen > 0) {
            System.arraycopy(classBytes, srcPos, result, dstPos, copyLen)
            dstPos += copyLen
            srcPos += copyLen
        }

        val replacement = newEntries[entry.dataPos]
        if (replacement != null) {
            val newLen = replacement.second.size
            result[dstPos++] = ((newLen shr 8) and 0xFF).toByte()
            result[dstPos++] = (newLen and 0xFF).toByte()
            srcPos += 2

            System.arraycopy(replacement.second, 0, result, dstPos, newLen)
            dstPos += newLen
            srcPos += entry.length
        } else {
            // No replacement — copy the original length + data verbatim
            val totalLen = 2 + entry.length // 2 bytes for length field + data
            System.arraycopy(classBytes, srcPos, result, dstPos, totalLen)
            dstPos += totalLen
            srcPos += totalLen
        }
    }

    val remaining = classBytes.size - srcPos
    if (remaining > 0) {
        System.arraycopy(classBytes, srcPos, result, dstPos, remaining)
    }

    return result
}

fun readU2(data: ByteArray, offset: Int): Int {
    return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}

fun ByteArray.containsSeq(seq: ByteArray): Boolean {
    if (seq.isEmpty()) return true
    for (i in indices) {
        if (i + seq.size > size) break
        var match = true
        for (j in seq.indices) {
            if (this[i + j] != seq[j]) { match = false; break }
        }
        if (match) return true
    }
    return false
}
