plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 26.1.2 entry point"

minecraft {
    useDefaultAccessTransformer()
}

dependencies {
    implementation(project(":versions:26.1.2:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:26.1.2-64.0.8"))

    // Forge 1.21+ splits its runtime into multiple artifacts.  The mavenizer
    // output for the `forge` jar does **not** contain @Mod or FMLPaths, so we
    // must pull the satellite libraries explicitly.
    implementation("net.minecraftforge:javafmllanguage:26.1.2-64.0.8")
    implementation("net.minecraftforge:fmlloader:26.1.2-64.0.8")
    implementation("net.minecraftforge:fmlcore:26.1.2-64.0.8")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") { expand("version" to project.version) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-26.1.2",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-26.1.2",
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
    val verCommonJar = project(":versions:26.1.2:common").tasks.named<Jar>("jar")
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
    description = "Rebuilds the fat jar with com.mojang classes relocated"
    dependsOn("fatJar")

    val inputJarFile = tasks.named<Jar>("fatJar").flatMap { it.archiveFile }
    val outputJarFile = layout.buildDirectory.file("libs/${project.name}-${project.version}-all.jar")

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
        if (entry.dataPos !in newEntries) continue

        val bytesBefore = entry.dataPos - srcPos
        if (bytesBefore > 0) {
            System.arraycopy(classBytes, srcPos, result, dstPos, bytesBefore)
            srcPos += bytesBefore
            dstPos += bytesBefore
        }

        val (_, newBytes) = newEntries[entry.dataPos]!!
        val newLen = newBytes.size

        val lengthFieldDstPos = dstPos - (entry.dataPos - entry.lengthFieldPos)
        result[lengthFieldDstPos] = (newLen shr 8).toByte()
        result[lengthFieldDstPos + 1] = (newLen and 0xFF).toByte()

        System.arraycopy(newBytes, 0, result, dstPos, newBytes.size)
        srcPos += entry.length
        dstPos += newLen
    }

    if (srcPos < classBytes.size) {
        System.arraycopy(classBytes, srcPos, result, dstPos, classBytes.size - srcPos)
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
