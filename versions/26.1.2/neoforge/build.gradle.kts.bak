plugins {
    alias(libs.plugins.moddev)
}

description = "MSF NeoForge 26.1.2 entry point"

neoForge {
    version = libs.versions.neoforge.get()
}

dependencies {
    implementation(project(":versions:26.1.2:common"))

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
    "Specification-Title"     to "MSF-neoforge-26.1.2",
    "Specification-Version"   to project.version,
    "Implementation-Title"    to "MSF-neoforge-26.1.2",
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
    val verCommonJar = project(":versions:26.1.2:common").tasks.named<Jar>("jar")
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
        val verCommonJarOut = project(":versions:26.1.2:common").tasks.named<Jar>("jar").get().archiveFile.get().asFile

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

        logger.lifecycle("Replacements (${replacements.size}):")
        replacements.forEach { (k, v) -> logger.lifecycle("  $k -> $v") }

        // Sort replacements by key length descending so longer matches take priority
        val sortedReplacements = replacements.entries.sortedByDescending { it.key.length }

        // Patch all .class files
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            val patched = patchClassConstantPool(original, sortedReplacements)
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

    // Collect Utf8 entries: list of (entryStartPos, lengthFieldPos, length, stringValue)
    data class Utf8Entry(
        val lengthFieldPos: Int,  // position of the 2-byte length field
        val dataPos: Int,         // position of the UTF-8 data
        val length: Int,          // length value in the length field
        val value: String         // decoded string
    )

    val utf8Entries = mutableListOf<Utf8Entry>()
    var anyMatch = false

    var i = 1
    while (i < cpCount && pos < classBytes.size) {
        val tag = classBytes[pos].toInt() and 0xFF
        pos += 1

        when (tag) {
            1 -> { // CONSTANT_Utf8
                val lengthFieldPos = pos
                val length = readU2(classBytes, pos)
                pos += 2
                val dataPos = pos
                if (pos + length > classBytes.size) break
                val utf8Str = String(classBytes.copyOfRange(pos, pos + length), Charsets.UTF_8)
                utf8Entries.add(Utf8Entry(lengthFieldPos, dataPos, length, utf8Str))
                pos += length

                // Quick match check
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

    // ---- Compute new strings for each Utf8 entry ----
    // For each entry, find the first matching replacement (longest key first)
    // and replace ONLY that match (not recursive).
    // We track which ranges in the original string have been replaced to avoid
    // overlapping replacements.

    val newEntries = mutableMapOf<Int, Pair<String, ByteArray>>() // dataPos -> (newString, newBytes)
    var totalSizeDelta = 0

    for (entry in utf8Entries) {
        val original = entry.value
        // Find all non-overlapping replacements to apply, sorted by position
        data class ReplacementRange(val start: Int, val end: Int, val replacement: String)
        val ranges = mutableListOf<ReplacementRange>()

        for ((old, new_) in sortedReplacements) {
            var searchFrom = 0
            while (true) {
                val idx = original.indexOf(old, searchFrom)
                if (idx < 0) break
                // Check if this range overlaps with an already-planned replacement
                val overlaps = ranges.any { r -> idx < r.end && (idx + old.length) > r.start }
                if (!overlaps) {
                    ranges.add(ReplacementRange(idx, idx + old.length, new_))
                }
                searchFrom = idx + 1
            }
        }

        if (ranges.isEmpty()) continue

        // Sort by start position
        ranges.sortBy { it.start }

        // Build the new string
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

    // ---- Rebuild the byte array ----
    val result = ByteArray(classBytes.size + totalSizeDelta)
    var srcPos = 0
    var dstPos = 0

    for (entry in utf8Entries) {
        if (entry.dataPos !in newEntries) continue

        // Copy everything before this entry's data
        val bytesBefore = entry.dataPos - srcPos
        if (bytesBefore > 0) {
            System.arraycopy(classBytes, srcPos, result, dstPos, bytesBefore)
            srcPos += bytesBefore
            dstPos += bytesBefore
        }

        val (_, newBytes) = newEntries[entry.dataPos]!!
        val newLen = newBytes.size

        // Update the length field (2 bytes before data)
        // The length field position is at entry.lengthFieldPos
        // We need to write the new length at the corresponding position in result
        // Since we may have already shifted bytes, calculate the position
        val lengthFieldDstPos = dstPos - (entry.dataPos - entry.lengthFieldPos)
        result[lengthFieldDstPos] = (newLen shr 8).toByte()
        result[lengthFieldDstPos + 1] = (newLen and 0xFF).toByte()

        // Copy new data
        System.arraycopy(newBytes, 0, result, dstPos, newBytes.size)
        srcPos += entry.length
        dstPos += newLen
    }

    // Copy remaining bytes after last modified entry
    if (srcPos < classBytes.size) {
        System.arraycopy(classBytes, srcPos, result, dstPos, classBytes.size - srcPos)
    }

    return result
}

fun readU2(data: ByteArray, offset: Int): Int {
    return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
