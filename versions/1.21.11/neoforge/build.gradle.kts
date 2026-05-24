plugins {
    alias(libs.plugins.moddev)
}

description = "MSF NeoForge 1.21.11 entry point"

neoForge {
    version = libs.versions.neoforge12111.get()
}

dependencies {
    // Exclude authlib from the common transitive chain — NeoForge 1.21.11 ships authlib 7.0.61
    // (strictly pinned) which conflicts with common's 7.0.63.  Our fat jar relocates the
    // com.mojang.authlib.yggdrasil classes anyway, so the exact authlib version on the
    // compile/runtime classpath only matters for NeoForge's own use.
    implementation(project(":versions:1.21.11:common")) {
        exclude(group = "com.mojang", module = "authlib")
    }

    compileOnlyApi(libs.sponge.mixin)

    // WebRTC Java API — bundled into the fat jar
    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

val neoforgeManifestAttrs = mapOf(
    "Specification-Title"     to "MSF-neoforge-1.21.11",
    "Specification-Version"   to project.version,
    "Implementation-Title"    to "MSF-neoforge-1.21.11",
    "Implementation-Version"  to project.version,
    "MixinConfigs"            to "msf-friends.mixins.json"
)

tasks.named<Jar>("jar") {
    manifest { attributes(neoforgeManifestAttrs) }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a deployable fat jar containing all module classes"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes(neoforgeManifestAttrs) }
    from(sourceSets.main.get().output)
    val commonJar = project(":common").tasks.named<Jar>("jar")
    val verCommonJar = project(":versions:1.21.11:common").tasks.named<Jar>("jar")
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(verCommonJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }

    // Bundle webrtc-java classes (exclude module-info to avoid JPMS conflicts)
    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }

    exclude("com/mojang/**")
}

tasks.named("assemble") { dependsOn("relocateFatJar") }

tasks.register("relocateFatJar") {
    group = "build"
    description = "Rebuilds the fat jar with com.mojang classes relocated"
    dependsOn("fatJar")

    val inputJarFile = tasks.named<Jar>("fatJar").flatMap { it.archiveFile }
    val outputJarFile = layout.buildDirectory.file("libs/${project.name}-${project.version}-all.jar")

    inputs.file(inputJarFile)
    outputs.file(outputJarFile)

    doLast {
        val srcPkg = "com/mojang/authlib/yggdrasil"
        val dstPkg = "dev/msf/friends/shaded/com/mojang/authlib/yggdrasil"

        // Authlib core classes that must NOT be relocated
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

        // Build replacement map: only for our custom classes
        val replacements = mutableMapOf<String, String>()

        for (internalName in shadedClassInternalNames) {
            val simpleName = internalName.substringAfterLast('/')
            val baseName = simpleName.substringBefore('$')
            if (baseName in authlibCoreSimpleNames) continue

            // Binary form: com/mojang/authlib/yggdrasil/FriendsService
            replacements["$srcPkg/$internalName"] = "$dstPkg/$internalName"
            // Dot form: com.mojang.authlib.yggdrasil.FriendsService
            val oldDot = "$srcPkg/$internalName".replace('/', '.')
            val newDot = "$dstPkg/$internalName".replace('/', '.')
            replacements[oldDot] = newDot
        }

        logger.lifecycle("Authlib relocation: ${replacements.size} entries")

        // === Strip Fabric refmap and fix mixin config for NeoForge ===
        // Same as Forge — the common module may still have a remnant refmap.
        // The refmap filename matches the "refmap" key in msf-friends.mixins.json.
        val refmapFile = File(unpacked, "versions-1.21.11-common-refmap.json")
        val mixinJsonFile = File(unpacked, "msf-friends.mixins.json")

        // --- Build intermediary → named (Mojang) remapping table from tiny mappings ---
        // Fabric Loom compiles mixin bytecode using intermediary names (class_XXX, method_XXXXX,
        // field_XXXXX).  NeoForge 1.21+ uses Mojang official names at runtime, so we must remap
        // all intermediary references in the fat jar's .class files to their Mojang counterparts.
        val intermediaryToNamed = mutableMapOf<String, String>()

        // 1) Parse the Fabric Loom tiny mapping file (intermediary → named → official)
        //    to build class-name and method/field mappings.
        //    Note: method/field lines in tiny v2 have a leading tab; we trimStart() before parsing.
        val loomCacheDir = File(project.gradle.gradleUserHomeDir, "caches/fabric-loom/1.21.11")
        val layeredDir = loomCacheDir.listFiles()
            ?.filter { it.name.startsWith("loom.mappings.1_21_11.layered") }
            ?.maxByOrNull { it.name }
        val tinyFile = if (layeredDir != null) File(layeredDir, "mappings-base.tiny") else null
        if (tinyFile != null && tinyFile.exists()) {
            logger.lifecycle("Using layered mapping file: $tinyFile")
            val methodByIntermediaryAndDesc = mutableMapOf<String, String>()  // "desc|intermediary" -> named
            val fieldByIntermediaryAndOwner = mutableMapOf<String, String>()  // "owner|intermediary" -> named

            tinyFile.readLines().forEach { line ->
                val parts = line.trimStart().split('\t')
                when {
                    parts.isEmpty() -> return@forEach
                    parts[0] == "c" && parts.size >= 3 -> {
                        intermediaryToNamed[parts[1]] = parts[2]
                    }
                    parts[0] == "m" && parts.size >= 4 -> {
                        if (parts[3] != parts[2]) {
                            methodByIntermediaryAndDesc["${parts[1]}|${parts[2]}"] = parts[3]
                        }
                    }
                    parts[0] == "f" && parts.size >= 4 -> {
                        if (parts[3] != parts[2]) {
                            fieldByIntermediaryAndOwner["${parts[1]}|${parts[2]}"] = parts[3]
                        }
                    }
                }
            }
            logger.lifecycle("Parsed tiny mappings: ${intermediaryToNamed.size} classes, ${methodByIntermediaryAndDesc.size} methods, ${fieldByIntermediaryAndOwner.size} fields")

            // 2) Collect intermediary method/field names actually used in our mixin .class files
            val usedMethods = mutableSetOf<String>()
            val usedFields = mutableSetOf<String>()
            unpacked.walkTopDown()
                .filter { it.name.endsWith(".class") && it.path.contains("mixin") }
                .forEach { classFile ->
                    val text = String(classFile.readBytes(), Charsets.ISO_8859_1)
                    Regex("""method_(\d+)""").findAll(text).forEach { usedMethods.add("method_${it.groupValues[1]}") }
                    Regex("""field_(\d+)""").findAll(text).forEach { usedFields.add("field_${it.groupValues[1]}") }
                }

            // 3) For methods/fields: intermediary names are NOT globally unique.
            //    Only add mappings where the intermediary name maps to exactly one named name.
            val methodMappingCandidates = mutableMapOf<String, MutableSet<String>>()
            methodByIntermediaryAndDesc.forEach { (key, named) ->
                val intermediary = key.substringAfterLast('|')
                if (intermediary in usedMethods) {
                    methodMappingCandidates.getOrPut(intermediary) { mutableSetOf() }.add(named)
                }
            }
            methodMappingCandidates.forEach { (intermediary, namedNames) ->
                if (namedNames.size == 1) {
                    intermediaryToNamed[intermediary] = namedNames.first()
                } else {
                    logger.lifecycle("  SKIP method $intermediary: ${namedNames.size} conflicting named mappings")
                }
            }

            // 4) Hard-coded overrides for lambda/synthetic methods where Fabric
            //    has no friendly name (intermediary == named in the tiny file).
            //    These appear in @Redirect/@Inject method annotations and must
            //    match the method name at NeoForge runtime.  The names below
            //    come from javap on the NeoForge merged jar's target classes.
            //
            //    method_19851 → ShareToLanScreen.lambda$init$2
            //      desc: (IntegratedServer;Button)V — "Start LAN" button callback
            //    method_56152 → ClientHandshakePacketListenerImpl.lambda$setEncryption$2
            //      desc: (Cipher;Cipher)V — setEncryption lambda
            val lambdaOverrides = mapOf(
                "method_19851" to "lambda\$init\$2",
                "method_56152" to "lambda\$setEncryption\$2",
            )
            for ((intermediary, mojangName) in lambdaOverrides) {
                if (intermediary in usedMethods) {
                    intermediaryToNamed[intermediary] = mojangName
                    logger.lifecycle("  OVERRIDE $intermediary -> $mojangName (lambda method)")
                }
            }

            val fieldMappingCandidates = mutableMapOf<String, MutableSet<String>>()
            fieldByIntermediaryAndOwner.forEach { (key, named) ->
                val intermediary = key.substringAfterLast('|')
                if (intermediary in usedFields) {
                    fieldMappingCandidates.getOrPut(intermediary) { mutableSetOf() }.add(named)
                }
            }
            fieldMappingCandidates.forEach { (intermediary, namedNames) ->
                if (namedNames.size == 1) {
                    intermediaryToNamed[intermediary] = namedNames.first()
                } else {
                    logger.lifecycle("  SKIP field $intermediary: ${namedNames.size} conflicting named mappings")
                }
            }

            val mfCount = methodMappingCandidates.size + fieldMappingCandidates.size
            logger.lifecycle("Intermediary→Named: ${intermediaryToNamed.size} classes + $mfCount method/field entries")
        } else {
            logger.warn("Tiny mapping file not found at $tinyFile — skipping intermediary remapping!")
        }

        // --- Add intermediary→named replacements to the patch table ---
        for ((intermediary, named) in intermediaryToNamed) {
            when {
                intermediary.startsWith("net/minecraft/class_") -> {
                    replacements[intermediary] = named
                    replacements[intermediary.replace('/', '.')] = named.replace('/', '.')
                }
                intermediary.startsWith("class_") -> {
                    val fullOld = "net/minecraft/$intermediary"
                    replacements[fullOld] = named
                    replacements[fullOld.replace('/', '.')] = named.replace('/', '.')
                }
                intermediary.startsWith("method_") || intermediary.startsWith("field_") -> {
                    replacements[intermediary] = named
                }
            }
        }

        // Use two replacement tables:
        // 1) mixinReplacements: full table (class + method + field + authlib) — for mixin .class files only
        // 2) otherReplacements: class names + authlib only — for all other .class files
        //    (replacing bare method_/field_ names in non-mixin classes risks false positives)
        val mixinReplacements = replacements.entries.sortedByDescending { it.key.length }
        val otherReplacements = replacements.filterKeys { key ->
            !key.startsWith("method_") && !key.startsWith("field_")
                    || key.startsWith("net/minecraft/class_")
                    || key.startsWith("net.minecraft.class_")
        }.entries.sortedByDescending { it.key.length }

        if (refmapFile.exists()) {
            refmapFile.delete()
            logger.lifecycle("Deleted Fabric refmap from NeoForge jar")
        }
        if (mixinJsonFile.exists()) {
            val jsonText = mixinJsonFile.readText()
            if (jsonText.contains("\"refmap\"")) {
                val cleaned = jsonText.replace(Regex(""",?\s*"refmap"\s*:\s*"[^"]*""""), "")
                    .replace(Regex("""\{\s*,"""), "{")
                mixinJsonFile.writeText(cleaned)
                logger.lifecycle("Removed refmap key from NeoForge mixin JSON")
            }
        }

        // Patch .class files
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

/**
 * Patches a Java class file by modifying CONSTANT_Utf8 entries in the constant pool.
 * Uses precise, non-overlapping replacements sorted by key length (longest first).
 * Each Utf8 entry is matched at most once per replacement key, and replacement
 * output is never re-scanned for further replacements.
 *
 * Strategy: Single-pass — parse all Utf8 entries, compute the new string for each,
 * then rebuild the entire byte array with all updates applied at once.
 */
fun patchClassConstantPool(
    classBytes: ByteArray,
    sortedReplacements: List<Map.Entry<String, String>>
): ByteArray {
    // ---- Parse constant pool to find all Utf8 entries ----
    if (classBytes.size < 10) return classBytes

    var pos = 0
    pos += 4 // magic
    pos += 2 // minor
    pos += 2 // major

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
                        if (utf8Str.contains(old)) { anyMatch = true; break }
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

    // ---- Compute new strings for each Utf8 entry ----
    data class ReplacementRange(val start: Int, val end: Int, val replacement: String)

    val newEntries = mutableMapOf<Int, Pair<String, ByteArray>>()
    var totalSizeDelta = 0

    for (entry in utf8Entries) {
        val original = entry.value
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
            if (range.start > lastEnd) sb.append(original.substring(lastEnd, range.start))
            sb.append(range.replacement)
            lastEnd = range.end
        }
        if (lastEnd < original.length) sb.append(original.substring(lastEnd))

        val newStr = sb.toString()
        val newBytes = newStr.toByteArray(Charsets.UTF_8)
        newEntries[entry.dataPos] = Pair(newStr, newBytes)

        totalSizeDelta += (newBytes.size - entry.length)
    }

    if (newEntries.isEmpty()) return classBytes

    // ---- Rebuild class bytes with updated Utf8 entries ----
    val newClassBytes = ByteArray(classBytes.size + totalSizeDelta)
    var srcPos = 0
    var dstPos = 0

    for (entry in utf8Entries) {
        // Copy everything before this entry's length field (tag is already copied)
        val copyUpTo = entry.lengthFieldPos
        val copyLen = copyUpTo - srcPos
        if (copyLen > 0) {
            System.arraycopy(classBytes, srcPos, newClassBytes, dstPos, copyLen)
            dstPos += copyLen
            srcPos += copyLen
        }

        val replacement = newEntries[entry.dataPos]
        if (replacement != null) {
            // Write new length (2 bytes, big-endian)
            val newLen = replacement.second.size
            newClassBytes[dstPos++] = ((newLen shr 8) and 0xFF).toByte()
            newClassBytes[dstPos++] = (newLen and 0xFF).toByte()
            srcPos += 2 // skip old length field

            // Write new UTF-8 data
            System.arraycopy(replacement.second, 0, newClassBytes, dstPos, newLen)
            dstPos += newLen
            srcPos += entry.length // skip old data
        }
    }

    // Copy remaining bytes after the last modified entry
    val remaining = classBytes.size - srcPos
    if (remaining > 0) {
        System.arraycopy(classBytes, srcPos, newClassBytes, dstPos, remaining)
    }

    return newClassBytes
}

fun readU2(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
