import java.net.URI

plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.20.1 entry point"

minecraft {
    useDefaultAccessTransformer()
}

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
    implementation(project(":versions:1.20.1:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.20.1-47.2.0"))

    implementation("net.minecraftforge:javafmllanguage:1.20.1-47.2.0")
    implementation("net.minecraftforge:fmlloader:1.20.1-47.2.0")
    implementation("net.minecraftforge:fmlcore:1.20.1-47.2.0")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.20.1",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.20.1",
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
    val verCommonJar = project(":versions:1.20.1:common").tasks.named<Jar>("jar")
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
        val verCommonJarOut = project(":versions:1.20.1:common").tasks.named<Jar>("jar").get().archiveFile.get().asFile

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

        // === SRG remapping for Forge 1.20.1 ===
        val refmapFile = File(unpacked, "versions-1.20.1-common-refmap.json")
        val mixinJsonFile = File(unpacked, "msf-friends.mixins.json")

        logger.lifecycle("Looking for refmap at: ${refmapFile.absolutePath}  exists=${refmapFile.exists()}")
        logger.lifecycle("Looking for mixin JSON at: ${mixinJsonFile.absolutePath}  exists=${mixinJsonFile.exists()}")

        // --- Locate mapping files ---
        val loomCacheDir = File(project.gradle.gradleUserHomeDir, "caches/fabric-loom/1.20.1")
        val layeredDir = loomCacheDir.listFiles()
            ?.filter { it.name.startsWith("loom.mappings.1_20_1.layered") }
            ?.maxByOrNull { it.name }
        val yarnMappingFile = if (layeredDir != null) File(layeredDir, "mappings-base.tiny") else null
        val effectiveYarnFile = if (yarnMappingFile != null && yarnMappingFile.exists()) yarnMappingFile else {
            val yarnVersion = libs.versions.yarn1201.get()
            val fallbackV2 = File(loomCacheDir, "net.fabricmc.yarn.1_20_1.${yarnVersion}-v2/mappings-base.tiny")
            val fallbackPlain = File(loomCacheDir, "net.fabricmc.yarn.1_20_1.${yarnVersion}/mappings-base.tiny")
            if (fallbackV2.exists()) fallbackV2 else fallbackPlain
        }

        val srgMappingFile = File(
            project.gradle.gradleUserHomeDir,
            "caches/minecraftforge/forgegradle/mavenizer/caches/mcp/de/oceanlabs/mcp/mcp_config" +
                "/1.20.1-20230612.114412/client/data/mappings/joined.tsrg"
        )

        logger.lifecycle("Yarn mapping file: $effectiveYarnFile (exists=${effectiveYarnFile.exists()})")
        logger.lifecycle("SRG mapping file: $srgMappingFile (exists=${srgMappingFile.exists()})")

        var mcClassInternalNames = emptySet<String>()

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
                                val desc = memberParts[1]
                                val srgName = memberParts[2]
                                srgMethodByKey["$currentObf|$desc|$obfMember"] = srgName
                            } else {
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

            val mojangMappingFile = project.rootProject.file("build/mojang-client-1.20.1.txt")
            if (!mojangMappingFile.exists()) {
                logger.lifecycle("Downloading Mojang ProGuard mapping for 1.20.1...")
                val url = URI("https://piston-data.mojang.com/v1/objects/6c48521eed01fe2e8ecdadbd5ae348415f3c47da/client.txt").toURL()
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
                        val mojangInternal = mojangFqcn.replace('.', '/')
                        val obfInternal = obfPart.replace('.', '/')
                        mojangClassByObf[obfInternal] = mojangInternal
                    }
                }
                logger.lifecycle("Parsed Mojang ProGuard: ${mojangClassByObf.size} classes")
            } else {
                logger.lifecycle("WARNING: Mojang ProGuard mapping not found")
            }

            var isV1 = false
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
                        when {
                            parts[0] == "c" && parts.size >= 3 -> {
                                obfToYarnClass[parts[1]] = parts[2]
                                obfToIntClass[parts[1]] = parts[1]
                            }
                        }
                    }
                }
            }
            logger.lifecycle("Parsed Yarn: ${obfToYarnClass.size} classes, ${methodObfKeyToYarn.size} methods, ${fieldObfKeyToYarn.size} fields")

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
            for ((yarnName, srgNames) in yarnMethodToSrgMultiMap) {
                if (srgNames.size == 1) {
                    replacements[yarnName] = srgNames.first()
                }
            }

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

            logger.lifecycle("SRG remapping table: ${replacements.size} total entries")

            mcClassInternalNames = replacements.keys.filter {
                it.contains('/') && !it.contains('.') && !it.startsWith("dev/")
            }.toSet()
        } else {
            logger.lifecycle("WARNING: Could not find mapping files — SRG remapping will NOT be applied!")
        }

        val pathQualified = replacements.entries.filter {
            it.key.contains('/') || it.key.contains('.')
        }.sortedByDescending { it.key.length }
        val bareNames = replacements.entries.filter {
            !it.key.contains('/') && !it.key.contains('.')
        }.sortedByDescending { it.key.length }

        logger.lifecycle("SRG remapping: ${pathQualified.size} path-qualified, ${bareNames.size} bare-name entries")

        // Convert Fabric refmap to Forge format
        if (refmapFile.exists()) {
            val refmapText = refmapFile.readText()

            val intClassToMojang = mutableMapOf<String, String>()
            for ((obf, intClass) in obfToIntClass) {
                val mojang = mojangClassByObf[obf] ?: continue
                intClassToMojang["L$intClass;"] = "L$mojang;"
            }
            logger.lifecycle("Refmap: intClassToMojang.size=${intClassToMojang.size}")

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

            fun transformRefmapValue(value: String): String {
                val semiIdx = value.indexOf(';')
                if (semiIdx < 0) return value

                val afterSemi = value.substring(semiIdx + 1)
                if (afterSemi.startsWith("field_") || afterSemi.startsWith("method_")) {
                    val parenIdx = afterSemi.indexOf('(')
                    if (parenIdx > 0) {
                        val methodName = afterSemi.substring(0, parenIdx)
                        val srgMethod = intMethodToSrg[methodName]
                        if (srgMethod != null) {
                            var classPart = value.substring(0, semiIdx + 1)
                            for ((intRef, mojangRef) in intClassToMojang) {
                                classPart = classPart.replace(intRef, mojangRef)
                            }
                            val descPart = afterSemi.substring(parenIdx)
                            var transformedDesc = descPart
                            for ((intRef, mojangRef) in intClassToMojang) {
                                transformedDesc = transformedDesc.replace(intRef, mojangRef)
                            }
                            return "${classPart}${srgMethod}${transformedDesc}"
                        }
                    }
                    var result = value
                    for ((intRef, mojangRef) in intClassToMojang) {
                        result = result.replace(intRef, mojangRef)
                    }
                    return result
                } else {
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
                    var result = value
                    for ((intRef, mojangRef) in intClassToMojang) {
                        result = result.replace(intRef, mojangRef)
                    }
                    return result
                }
            }

            val convertedText = refmapText.replace(Regex("\"([^\"]+)\"")) { match ->
                val str = match.groupValues[1]
                if (str.contains(";") && (str.contains("class_") || str.contains("field_") || str.contains("method_"))) {
                    "\"${transformRefmapValue(str)}\""
                } else {
                    match.value
                }
            }

            refmapFile.writeText(convertedText)
            logger.lifecycle("Converted Fabric refmap to Forge format")
        } else {
            logger.lifecycle("WARNING: No refmap found at ${refmapFile.absolutePath}")
        }

        // Apply SRG replacements to ALL class files
        var patchedCount = 0
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            val patched = patchClassConstantPool(original, pathQualified, bareNames, mcClassInternalNames)
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
    mcClassNames: Set<String> = emptySet()
): ByteArray {
    if (classBytes.size < 10) return classBytes

    var pos = 0
    pos += 4
    pos += 2
    pos += 2

    val cpCount = readU2(classBytes, pos)
    pos += 2

    data class Utf8Entry(
        val cpIndex: Int,
        val lengthFieldPos: Int,
        val dataPos: Int,
        val length: Int,
        val value: String
    )

    val utf8Indices = mutableMapOf<Int, Int>()
    val utf8Entries = mutableListOf<Utf8Entry>()
    val classRefs = mutableMapOf<Int, Int>()
    val nameAndTypeRefs = mutableMapOf<Int, Pair<Int, Int>>()
    val methodFieldRefs = mutableMapOf<Int, Pair<Int, Int>>()

    var anyPathMatch = false
    var anyBareMatch = false

    var i = 1
    var scanPos = pos
    while (i < cpCount && scanPos < classBytes.size) {
        val tag = classBytes[scanPos].toInt() and 0xFF
        scanPos += 1

        when (tag) {
            1 -> {
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
            3 -> { scanPos += 4 }
            4 -> { scanPos += 4 }
            5 -> { scanPos += 8; i++ }
            6 -> { scanPos += 8; i++ }
            7 -> {
                val nameIdx = readU2(classBytes, scanPos)
                classRefs[i] = nameIdx
                scanPos += 2
            }
            8 -> { scanPos += 2 }
            9 -> {
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            10 -> {
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            11 -> {
                val classIdx = readU2(classBytes, scanPos)
                val natIdx = readU2(classBytes, scanPos + 2)
                methodFieldRefs[i] = Pair(classIdx, natIdx)
                scanPos += 4
            }
            12 -> {
                val nameIdx = readU2(classBytes, scanPos)
                val descIdx = readU2(classBytes, scanPos + 2)
                nameAndTypeRefs[i] = Pair(nameIdx, descIdx)
                scanPos += 4
            }
            15 -> { scanPos += 3 }
            16 -> { scanPos += 2 }
            17 -> { scanPos += 4 }
            18 -> { scanPos += 4 }
            19 -> { scanPos += 2 }
            20 -> { scanPos += 2 }
            else -> break
        }
        i++
    }

    if (!anyPathMatch && !anyBareMatch) return classBytes
    if (!anyPathMatch) return classBytes

    val safeBareNameUtf8Positions = mutableSetOf<Int>()

    if (bareNames.isNotEmpty()) {
        val bareNameValues = bareNames.map { it.key }.toSet()

        for ((listIdx, entry) in utf8Entries.withIndex()) {
            if (entry.value !in bareNameValues) continue
            val utf8CpIndex = entry.cpIndex

            val referencingNATs = nameAndTypeRefs.filter { (_, nameDesc) -> nameDesc.first == utf8CpIndex }
            if (referencingNATs.isEmpty()) continue

            var allRefsAreMC = true
            var hasAnyRef = false

            for ((natCpIndex, natPair) in referencingNATs) {
                // Check if the NameAndType descriptor references MC classes
                // This handles @Shadow fields in mixins where the owning class is non-MC
                // but the field type (descriptor) references MC classes
                val descUtf8Index = natPair.second
                val descListIdx = utf8Indices[descUtf8Index]
                if (descListIdx != null) {
                    val descStr = utf8Entries[descListIdx].value
                    // Use regex to extract all object type references from JVM descriptors
                    // Handles both field descriptors (L...;) and method descriptors ((...)L...;)
                    val descRefsMC = Regex("L([^;]+);").findAll(descStr).any { match ->
                        val className = match.groupValues[1]
                        className.startsWith("net/minecraft/") && className in mcClassNames
                    }
                    if (descRefsMC) {
                        hasAnyRef = true
                        continue
                    }
                }

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

            if (hasAnyRef && allRefsAreMC) {
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
            val totalLen = 2 + entry.length
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