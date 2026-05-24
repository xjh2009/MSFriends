plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.21.11 entry point"

minecraft {
    useDefaultAccessTransformer()
}

dependencies {
    implementation(project(":versions:1.21.11:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.21.11-61.1.0"))

    // Forge 1.21+ splits its runtime into multiple artifacts.  The mavenizer
    // output for the `forge` jar does **not** contain @Mod or FMLPaths, so we
    // must pull the satellite libraries explicitly.
    implementation("net.minecraftforge:javafmllanguage:1.21.11-61.1.0")
    implementation("net.minecraftforge:fmlloader:1.21.11-61.1.0")
    implementation("net.minecraftforge:fmlcore:1.21.11-61.1.0")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") { expand("version" to project.version) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.21.11",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.21.11",
    "Implementation-Version" to project.version,
    "MixinConfigs" to "msf-friends.mixins.json",
    "FMLModType" to "GAMELIBRARY"
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
    val verCommonJar = project(":versions:1.21.11:common").tasks.named<Jar>("jar")
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
        val verCommonJarOut = project(":versions:1.21.11:common").tasks.named<Jar>("jar").get().archiveFile.get().asFile

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

        logger.lifecycle("Replacements (${replacements.size}):")
        replacements.forEach { (k, v) -> logger.lifecycle("  $k -> $v") }

        val sortedReplacements = replacements.entries.sortedByDescending { it.key.length }

        // === Strip Fabric intermediary names and fix mixin config for Forge ===
        // Fabric Loom remaps the common jar from official→intermediary.  The resulting
        // bytecode has @Inject targets like "net/minecraft/class_310" which Forge's
        // Mixin subsystem cannot understand.  We fix this by:
        //   1. Loading the Yarn intermediary→named mapping table from the Loom cache
        //   2. Adding intermediary→official (human-readable) replacements to the pool
        //      (class names for all .class files; method/field names for mixin .class only)
        //   3. Deleting the Fabric refmap and removing the "refmap" key from mixin JSON

        // The refmap filename matches the "refmap" key in msf-friends.mixins.json.
        val refmapFile = File(unpacked, "versions-1.21.11-common-refmap.json")
        val mixinJsonFile = File(unpacked, "msf-friends.mixins.json")

        logger.lifecycle("Looking for refmap at: ${refmapFile.absolutePath}  exists=${refmapFile.exists()}")
        logger.lifecycle("Looking for mixin JSON at: ${mixinJsonFile.absolutePath}  exists=${mixinJsonFile.exists()}")

        // Build intermediary→named remapping table from the layered Yarn mapping file.
        // The layered mapping has 3 columns: intermediary → named → official.
        // "official" in Yarn = obfuscated short names (e.g. "as"), NOT Mojang readable names.
        // "named" in Yarn = human-readable names (e.g. "keyMappings"), which for MC 1.21.11
        // match the Mojang Proguard names that Forge uses at runtime.
        // For classes: named = full package path (e.g. net/minecraft/client/Options), same as Forge runtime.
        // Therefore we always use the "named" column for all replacements.
        // IMPORTANT: We use the layered mapping (not the base yarn mapping) because the layered
        // version has updated Yarn named names that reflect Mojang official names for MC 1.21.11,
        // whereas the older base yarn mapping has stale Yarn names (e.g. allKeys vs keyMappings).
        val intermediaryToNamed = mutableMapOf<String, String>()

        // Use the layered mapping file which has up-to-date Yarn named names.
        val loomCacheDir = File(project.gradle.gradleUserHomeDir,
            "caches/fabric-loom/1.21.11")
        val layeredDir = loomCacheDir.listFiles()
            ?.filter { it.name.startsWith("loom.mappings.1_21_11.layered") }
            ?.maxByOrNull { it.name }
        val mappingFile = if (layeredDir != null) File(layeredDir, "mappings-base.tiny") else null

        if (mappingFile != null && mappingFile.exists()) {
            logger.lifecycle("Using layered mapping file: $mappingFile")
        }

        // Fall back to the base yarn mapping if layered is not found
        val effectiveMappingFile = if (mappingFile != null && mappingFile.exists()) mappingFile else {
            val yarnVersion = libs.versions.yarn12111.get()
            val fallback = File(loomCacheDir, "net.fabricmc.yarn.1_21_11.${yarnVersion}-v2/mappings-base.tiny")
            logger.lifecycle("Layered mapping not found, falling back to: $fallback")
            fallback
        }

        if (effectiveMappingFile.exists()) {
            var currentClassIntermediary = ""
            var currentClassNamed = ""
            val methodByIntermediaryAndDesc = mutableMapOf<String, String>()
            val fieldByIntermediaryAndOwner = mutableMapOf<String, String>()

            effectiveMappingFile.useLines { lines ->
                for (line in lines) {
                    val parts = line.trimStart().split("\t")
                    when {
                        parts.isEmpty() -> continue
                        parts[0] == "c" && parts.size >= 3 -> {
                            currentClassIntermediary = parts[1]
                            // Class lines have no leading tab, so column offsets differ from method/field lines.
                            // Format: c  intermediary  named  [official]
                            // named is always parts[2] for class lines.
                            currentClassNamed = parts[2]
                            intermediaryToNamed[currentClassIntermediary] = currentClassNamed
                        }
                        parts[0] == "m" && parts.size >= 4 -> {
                            val desc = parts[1]
                            val intermediary = parts[2]
                            // Method lines: m  desc  intermediary  named  [official]
                            // named is always parts[3] for method lines.
                            val named = parts[3]
                            if (named != intermediary) {
                                methodByIntermediaryAndDesc["$desc|$intermediary"] = named
                            }
                        }
                        parts[0] == "f" && parts.size >= 4 -> {
                            val owner = parts[1]
                            val intermediary = parts[2]
                            // Field lines: f  owner  intermediary  named  [official]
                            // named is always parts[3] for field lines.
                            val named = parts[3]
                            if (named != intermediary) {
                                fieldByIntermediaryAndOwner["$owner|$intermediary"] = named
                            }
                        }
                    }
                }
            }
            logger.lifecycle("Parsed class mappings: ${intermediaryToNamed.size}, method: ${methodByIntermediaryAndDesc.size}, field: ${fieldByIntermediaryAndOwner.size}")

            // Scan mixin .class files for method_ and field_ intermediary names
            val refmapMethods = mutableSetOf<String>()
            val refmapFields = mutableSetOf<String>()
            unpacked.walkTopDown()
                .filter { it.name.endsWith(".class") && it.path.contains("mixin") }
                .forEach { classFile ->
                    val bytes = classFile.readBytes()
                    val text = String(bytes, Charsets.ISO_8859_1)
                    Regex("""method_(\d+)""").findAll(text).forEach { m ->
                        refmapMethods.add("method_${m.groupValues[1]}")
                    }
                    Regex("""field_(\d+)""").findAll(text).forEach { m ->
                        refmapFields.add("field_${m.groupValues[1]}")
                    }
                }

            logger.lifecycle("Scanned mixin methods: ${refmapMethods.sorted()}")
            logger.lifecycle("Scanned mixin fields: ${refmapFields.sorted()}")

            // Add uniquely-mapped method names
            val methodNameToNamedNames = mutableMapOf<String, MutableSet<String>>()
            methodByIntermediaryAndDesc.forEach { (key, named) ->
                val intermediary = key.substringAfterLast('|')
                if (intermediary in refmapMethods) {
                    methodNameToNamedNames.getOrPut(intermediary) { mutableSetOf() }.add(named)
                }
            }
            methodNameToNamedNames.forEach { (intermediary, namedNames) ->
                if (namedNames.size == 1) {
                    intermediaryToNamed[intermediary] = namedNames.first()
                } else {
                    logger.lifecycle("  WARNING: method $intermediary has ${namedNames.size} named mappings: $namedNames — skipping")
                }
            }

            // Add uniquely-mapped field names
            val fieldNameToNamedNames = mutableMapOf<String, MutableSet<String>>()
            fieldByIntermediaryAndOwner.forEach { (key, named) ->
                val intermediary = key.substringAfterLast('|')
                if (intermediary in refmapFields) {
                    fieldNameToNamedNames.getOrPut(intermediary) { mutableSetOf() }.add(named)
                }
            }
            fieldNameToNamedNames.forEach { (intermediary, namedNames) ->
                if (namedNames.size == 1) {
                    intermediaryToNamed[intermediary] = namedNames.first()
                } else {
                    logger.lifecycle("  WARNING: field $intermediary has ${namedNames.size} named mappings: $namedNames — skipping")
                }
            }

            logger.lifecycle("Intermediary→Named remapping table: ${intermediaryToNamed.size} entries")
        } else {
            logger.lifecycle("WARNING: Could not find mappings at $effectiveMappingFile — intermediary names will NOT be replaced!")
        }

        // Add intermediary→named replacements to the replacement map
        for ((intermediary, named) in intermediaryToNamed) {
            when {
                intermediary.startsWith("net/minecraft/class_") -> {
                    // Slash-form class reference
                    replacements[intermediary] = named
                    // Dot-form class reference
                    replacements[intermediary.replace('/', '.')] = named.replace('/', '.')
                }
                intermediary.startsWith("class_") -> {
                    // Bare class reference -> full path
                    val fullOld = "net/minecraft/$intermediary"
                    val fullNew = named
                    replacements[fullOld] = fullNew
                    replacements[fullOld.replace('/', '.')] = fullNew.replace('/', '.')
                }
                intermediary.startsWith("method_") || intermediary.startsWith("field_") -> {
                    // Method/field name — add as-is for replacement in constant pool strings
                    replacements[intermediary] = named
                }
            }
        }

        // Build two replacement tables:
        // 1) mixinReplacements: full table (class + method + field + authlib) — only for mixin .class files
        // 2) otherReplacements: class names + authlib only — for all other .class files
        //    (replacing bare method_/field_ names in non-mixin classes risks false positives)
        val mixinReplacements = replacements.entries.sortedByDescending { it.key.length }
        val otherReplacements = replacements.filterKeys { key ->
            !key.startsWith("method_") && !key.startsWith("field_")
                    || key.startsWith("net/minecraft/class_")
                    || key.startsWith("net.minecraft.class_")
        }.entries.sortedByDescending { it.key.length }

        logger.lifecycle("Mixin replacements: ${mixinReplacements.size}, Other replacements: ${otherReplacements.size}")

        // Delete refmap if present
        if (refmapFile.exists()) {
            refmapFile.delete()
            logger.lifecycle("Deleted Fabric refmap from Forge jar")
        }

        // Remove "refmap" key from mixin JSON
        if (mixinJsonFile.exists()) {
            val jsonText = mixinJsonFile.readText()
            if (jsonText.contains("\"refmap\"")) {
                val cleaned = jsonText.replace(Regex(""",?\s*"refmap"\s*:\s*"[^"]*""""), "")
                    .replace(Regex("""\{\s*,"""), "{")
                mixinJsonFile.writeText(cleaned)
                logger.lifecycle("Removed refmap key from mixin JSON")
            }
        }

        // Apply replacements to class files (mixin classes get full table, others get class-only)
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val isMixinClass = classFile.path.contains("mixin")
            val replacementTable = if (isMixinClass) mixinReplacements else otherReplacements
            val original = classFile.readBytes()
            val patched = patchClassConstantPool(original, replacementTable)
            if (!patched.contentEquals(original)) {
                classFile.writeBytes(patched)
            }
        }

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
    sortedReplacements: List<Map.Entry<String, String>>
): ByteArray {
    if (classBytes.size < 10) return classBytes

    var pos = 0
    pos += 4
    pos += 2
    pos += 2

    val cpCount = readU2(classBytes, pos)
    pos += 2

    data class Utf8Entry(
        val lengthFieldPos: Int,
        val dataPos: Int,
        val length: Int,
        val value: String
    )

    val utf8Entries = mutableListOf<Utf8Entry>()
    var anyMatch = false

    var i = 1
    while (i < cpCount && pos < classBytes.size) {
        val tag = classBytes[pos].toInt() and 0xFF
        pos += 1

        when (tag) {
            1 -> {
                val lengthFieldPos = pos
                val length = readU2(classBytes, pos)
                pos += 2
                val dataPos = pos
                if (pos + length > classBytes.size) break
                val utf8Str = String(classBytes.copyOfRange(pos, pos + length), Charsets.UTF_8)
                utf8Entries.add(Utf8Entry(lengthFieldPos, dataPos, length, utf8Str))
                pos += length

                if (!anyMatch) {
                    for ((old, _) in sortedReplacements) {
                        if (utf8Str.contains(old)) {
                            anyMatch = true
                            break
                        }
                    }
                }
            }
            3 -> { pos += 4 }
            4 -> { pos += 4 }
            5 -> { pos += 8; i++ }
            6 -> { pos += 8; i++ }
            7 -> { pos += 2 }
            8 -> { pos += 2 }
            9 -> { pos += 4 }
            10 -> { pos += 4 }
            11 -> { pos += 4 }
            12 -> { pos += 4 }
            15 -> { pos += 3 }
            16 -> { pos += 2 }
            17 -> { pos += 4 }
            18 -> { pos += 4 }
            19 -> { pos += 2 }
            20 -> { pos += 2 }
            else -> break
        }
        i++
    }

    if (!anyMatch) return classBytes

    val newEntries = mutableMapOf<Int, Pair<String, ByteArray>>()
    var totalSizeDelta = 0

    for (entry in utf8Entries) {
        val original = entry.value
        data class ReplacementRange(val start: Int, val end: Int, val replacement: String)
        val ranges = mutableListOf<ReplacementRange>()

        for ((old, new_) in sortedReplacements) {
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
        // Copy everything before this entry's length field (tag byte is included)
        val copyUpTo = entry.lengthFieldPos
        val copyLen = copyUpTo - srcPos
        if (copyLen > 0) {
            System.arraycopy(classBytes, srcPos, result, dstPos, copyLen)
            dstPos += copyLen
            srcPos += copyLen
        }

        val replacement = newEntries[entry.dataPos]
        if (replacement != null) {
            // Write new length (2 bytes, big-endian)
            val newLen = replacement.second.size
            result[dstPos++] = ((newLen shr 8) and 0xFF).toByte()
            result[dstPos++] = (newLen and 0xFF).toByte()
            srcPos += 2 // skip old length field

            // Write new UTF-8 data
            System.arraycopy(replacement.second, 0, result, dstPos, newLen)
            dstPos += newLen
            srcPos += entry.length // skip old data
        }
    }

    // Copy remaining bytes after the last entry
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