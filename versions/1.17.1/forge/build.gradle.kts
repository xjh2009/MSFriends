import java.net.URI

plugins {
    alias(libs.plugins.forge.gradle)
}

description = "MSF Forge 1.17.1 entry point"

minecraft {
    useDefaultAccessTransformer()
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        when {
            requested.group == "cpw.mods" && requested.name == "modlauncher" -> useVersion("9.0.7")
            requested.group == "cpw.mods" && requested.name == "securejarhandler" -> useVersion("0.9.29")
            requested.group == "net.minecraftforge" && requested.name == "forgespi" -> useVersion("4.0.10")
            requested.group == "net.minecraftforge" && requested.name == "coremods" -> useVersion("5.0.1")
            requested.group == "net.minecraftforge" && requested.name == "eventbus" -> useVersion("5.0.7")
            requested.group == "org.apache.logging.log4j" && requested.name == "log4j-api" -> useVersion("2.17.0")
            requested.group == "org.apache.logging.log4j" && requested.name == "log4j-core" -> useVersion("2.17.0")
            requested.group == "net.jodah" && requested.name == "typetools" -> useVersion("0.8.3")
        }
    }
}

dependencies {
    implementation(project(":versions:1.17.1:common"))
    implementation(minecraft.dependency("net.minecraftforge:forge:1.17.1-37.1.1"))

    implementation("net.minecraftforge:javafmllanguage:1.17.1-37.1.1")
    implementation("net.minecraftforge:fmlloader:1.17.1-37.1.1")
    implementation("net.minecraftforge:fmlcore:1.17.1-37.1.1")

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    val safeVersion = project.version.toString().replace("+", "-")
    filesMatching("META-INF/mods.toml") { expand("version" to safeVersion) }
}

val forgeManifestAttrs = mapOf(
    "Specification-Title" to "MSF-forge-1.17.1",
    "Specification-Version" to project.version,
    "Implementation-Title" to "MSF-forge-1.17.1",
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
    val verCommonJar = project(":versions:1.17.1:common").tasks.named<Jar>("jar")
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
    description = "Rebuilds the fat jar with com.mojang classes relocated and Fabric intermediary names remapped to SRG"
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
        val verCommonJarOut = project(":versions:1.17.1:common").tasks.named<Jar>("jar").get().archiveFile.get().asFile

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

        // === SRG remapping for Forge 1.17.1 ===
        val refmapFile = File(unpacked, "versions-1.17.1-common-refmap.json")
        val mixinJsonFile = File(unpacked, "msf-friends.mixins.json")

        logger.lifecycle("Looking for refmap at: ${refmapFile.absolutePath}  exists=${refmapFile.exists()}")
        logger.lifecycle("Looking for mixin JSON at: ${mixinJsonFile.absolutePath}  exists=${mixinJsonFile.exists()}")

        // --- Locate mapping files ---
        val yarnMappingFile = File(
            project.gradle.gradleUserHomeDir,
            "caches/fabric-loom/1.17.1/net.fabricmc.yarn.1_17_1.1.17.1+build.38/mappings-base.tiny"
        )

        val srgMappingFile = File(
            project.gradle.gradleUserHomeDir,
            "caches/minecraftforge/forgegradle/mavenizer/caches/mcp/de/oceanlabs/mcp/mcp_config" +
                "/1.17.1-20210706.113038/client/data/mappings/joined.tsrg"
        )

        logger.lifecycle("Yarn mapping file: $yarnMappingFile (exists=${yarnMappingFile.exists()})")
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

        if (yarnMappingFile.exists() && srgMappingFile.exists()) {
            // Parse TSRG2 format
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

            val mojangMappingFile = project.rootProject.file("build/mojang-client-1.17.1.txt")
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

            // Yarn 1.17.1 uses v1 tiny format
            yarnMappingFile.useLines { lines ->
                for (line in lines) {
                    val parts = line.trimStart().split("\t")
                    if (parts.isEmpty()) continue
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
            // Also allow com.mojang classes (RenderSystem etc.) for bare name safety checks
            mcClassInternalNames = mcClassInternalNames + setOf(
                "com/mojang/blaze3d/systems/RenderSystem",
                "com/mojang/blaze3d/platform/TextureUtil",
                "com/mojang/blaze3d/vertex/BufferUploader",
                "com/mojang/blaze3d/vertex/Tesselator",
                "com/mojang/blaze3d/vertex/BufferBuilder",
                "com/mojang/blaze3d/platform/GlStateManager"
            )
        } else {
            logger.lifecycle("WARNING: Could not find mapping files — SRG remapping will NOT be applied!")
        }

        val pathQualified = replacements.entries.filter {
            it.key.contains('/') || it.key.contains('.')
        }.sortedByDescending { it.key.length }
        val bareNames = replacements.entries.filter {
            !it.key.contains('/') && !it.key.contains('.')
        }.sortedByDescending { it.key.length }

        // Forced bare name overrides for ambiguous Yarn→SRG mappings
        // These cannot be auto-detected because the same Yarn name maps to
        // different SRG names on different classes
        val forcedBareNames = mapOf(
            "getMessage" to "m_6035_",       // AbstractWidget.getMessage (NOT m_5667_ on Component!)
            "addDrawableChild" to "m_142416_", // Screen.addDrawableChild
            "parent" to "f_96643_",           // OpenToLanScreen.parent (Screen parent field)
            "EMPTY" to "f_131282_",           // LiteralText.EMPTY
            // RenderSystem methods (com.mojang package, ambiguous bare names)
            "setShaderTexture" to "m_157456_", // RenderSystem.setShaderTexture(int, ResourceLocation)
            "setShaderColor" to "m_157429_",   // RenderSystem.setShaderColor(float,float,float,float)
            "enableBlend" to "m_69478_",       // RenderSystem.enableBlend()
            "disableBlend" to "m_69461_",      // RenderSystem.disableBlend()
            "enableDepthTest" to "m_69482_",   // RenderSystem.enableDepthTest()
            "disableDepthTest" to "m_69465_",   // RenderSystem.disableDepthTest()
            "alpha" to "f_93625_",             // ClickableWidget.alpha (float field)
            "x" to "f_93620_",               // ClickableWidget.x (int field)
            "y" to "f_93621_",               // ClickableWidget.y (int field)
            "active" to "f_93623_",           // ClickableWidget.active
            "visible" to "f_93624_",          // ClickableWidget.visible
            "focused" to "f_93622_",           // ClickableWidget.focused
            "setMessage" to "m_93666_",       // ClickableWidget.setMessage
            "setWidth" to "m_93674_",          // ClickableWidget.setWidth
            "drawTexture" to "m_93133_",        // DrawableHelper.drawTexture(PoseStack,int,int,float,float,int,int,int,int)
            "onPress" to "m_93750_"             // Button$PressAction.onPress(Button)
        ).entries.map { object : Map.Entry<String, String> {
            override val key = it.key
            override val value = it.value
        } }
        // Merge forced names into bareNames (forced takes priority)
        val forcedKeys = forcedBareNames.map { it.key }.toSet()
        val mergedBareNames = forcedBareNames + bareNames.filter { it.key !in forcedKeys }

        logger.lifecycle("SRG remapping: ${pathQualified.size} path-qualified, ${mergedBareNames.size} bare-name entries (${forcedBareNames.size} forced)")

    // Refmap: convert Fabric intermediary names to SRG/Mojang names for Forge
    if (refmapFile.exists()) {
        logger.lifecycle("Converting Fabric refmap to SRG/Mojang names")
        val refmapText = refmapFile.readText()
        // Parse the refmap JSON manually to avoid adding json lib dependency
        var convertedRefmap = refmapText

        // Build reverse maps: intermediary class → obfuscated → Mojang class
        val intToObfClass = mutableMapOf<String, String>()
        for ((obf, intClass) in obfToIntClass) {
            intToObfClass[intClass] = obf
        }
        // Also build intermediary method → SRG method via obfKey
        val intMethodToSrg = mutableMapOf<String, String>()
        for ((obfKey, intMethod) in methodObfKeyToInt) {
            val srgMethod = srgMethodByKey[obfKey] ?: continue
            if (intMethod != srgMethod) {
                intMethodToSrg[intMethod] = srgMethod
            }
        }
        // intermediary field → SRG field via obfKey
        val intFieldToSrg = mutableMapOf<String, String>()
        for ((obfKey, intField) in fieldObfKeyToInt) {
            val srgField = srgFieldByKey[obfKey] ?: continue
            if (intField != srgField) {
                intFieldToSrg[intField] = srgField
            }
        }

        // Convert class paths in refmap: intermediary class_NNN → Mojang class name
        val classPattern = Regex("Lnet/minecraft/class_(\\d+);")
        convertedRefmap = classPattern.replace(convertedRefmap) { match ->
            val fullIntClass = "net/minecraft/class_${match.groupValues[1]}"
            val obfClass = intToObfClass[fullIntClass]
            val mojangClass = if (obfClass != null) mojangClassByObf[obfClass] else null
            if (mojangClass != null) {
                logger.lifecycle("  refmap class: $fullIntClass -> $mojangClass")
                "L$mojangClass;"
            } else {
                match.value // keep original if not found
            }
        }

        // Also convert plain class paths (without L...; prefix) in refmap data section
        val plainClassPattern = Regex("\"(net/minecraft/class_(\\d+))\"")
        convertedRefmap = plainClassPattern.replace(convertedRefmap) { match ->
            val fullIntClass = match.groupValues[1]
            val obfClass = intToObfClass[fullIntClass]
            val mojangClass = if (obfClass != null) mojangClassByObf[obfClass] else null
            if (mojangClass != null) {
                "\"$mojangClass\""
            } else {
                match.value
            }
        }

        // Convert intermediary method names: method_NNN → SRG name
        val methodPattern = Regex("method_(\\d+)")
        convertedRefmap = methodPattern.replace(convertedRefmap) { match ->
            val intName = match.value
            val srgName = intMethodToSrg[intName]
            if (srgName != null) {
                logger.lifecycle("  refmap method: $intName -> $srgName")
                srgName
            } else {
                intName // keep original if not found
            }
        }

        // Convert intermediary field names: field_NNN → SRG name
        val fieldPattern = Regex("field_(\\d+)")
        convertedRefmap = fieldPattern.replace(convertedRefmap) { match ->
            val intName = match.value
            val srgName = intFieldToSrg[intName]
            if (srgName != null) {
                logger.lifecycle("  refmap field: $intName -> $srgName")
                srgName
            } else {
                intName // keep original if not found
            }
        }

        refmapFile.writeText(convertedRefmap)
        logger.lifecycle("Refmap converted and written to ${refmapFile.absolutePath}")
    } else {
        logger.lifecycle("WARNING: No refmap found at ${refmapFile.absolutePath}")
    }

        // Build class → {yarnFieldName → srgFieldName} for inherited field resolution
        // Index by BOTH Mojang class name AND Yarn class name so inherited resolution
        // works regardless of whether path-qualified replacement has run on the bytecode
        val mojangClassFields = mutableMapOf<String, MutableMap<String, String>>()
        logger.lifecycle("Building mojangClassFields: fieldObfKeyToYarn.size=${fieldObfKeyToYarn.size}, srgFieldByKey.size=${srgFieldByKey.size}, mojangClassByObf.size=${mojangClassByObf.size}")
        var mojangFieldHits = 0
        for ((obfKey, yarnField) in fieldObfKeyToYarn) {
            val srgField = srgFieldByKey[obfKey] ?: continue
            val obfClass = obfKey.substringBefore('|')
            val mojangClass = mojangClassByObf[obfClass] ?: continue
            if (yarnField != srgField) {
                mojangClassFields.getOrPut(mojangClass) { mutableMapOf() }[yarnField] = srgField
                // Also index by Yarn class name for inherited resolution
                val yarnClass = obfToYarnClass[obfClass]
                if (yarnClass != null && yarnClass != mojangClass) {
                    mojangClassFields.getOrPut(yarnClass) { mutableMapOf() }[yarnField] = srgField
                }
                mojangFieldHits++
            }
        }
        logger.lifecycle("mojangClassFields: ${mojangClassFields.size} classes, $mojangFieldHits total field entries")

        // Propagate fields through known class hierarchies (Yarn class names)
        // This handles the case where a field is defined on a grandparent class
        // but the direct parent doesn't have its own field entry
        val hierarchy = mapOf(
            // Widget hierarchy
            "net/minecraft/client/gui/widget/ButtonWidget" to "net/minecraft/client/gui/widget/ClickableWidget",
            "net/minecraft/client/gui/widget/AlwaysSelectedEntryListWidget" to "net/minecraft/client/gui/widget/EntryListWidget",
            "net/minecraft/client/gui/widget/EntryListWidget" to "net/minecraft/client/gui/screen/Screen",
            "net/minecraft/client/gui/widget/TextFieldWidget" to "net/minecraft/client/gui/widget/ClickableWidget",
            // Screen hierarchy
            "net/minecraft/client/gui/screen/GameMenuScreen" to "net/minecraft/client/gui/screen/Screen",
            "net/minecraft/client/gui/screen/OpenToLanScreen" to "net/minecraft/client/gui/screen/Screen",
            // Also map Mojang names
            "net/minecraft/client/gui/components/Button" to "net/minecraft/client/gui/components/AbstractWidget",
            "net/minecraft/client/gui/components/EditBox" to "net/minecraft/client/gui/components/AbstractWidget"
        )
        var propagated = 0
        for ((child, parent) in hierarchy) {
            val parentFields = mojangClassFields[parent] ?: continue
            val childFields = mojangClassFields.getOrPut(child) { mutableMapOf() }
            for ((name, srg) in parentFields) {
                if (name !in childFields) {
                    childFields[name] = srg
                    propagated++
                }
            }
        }
        logger.lifecycle("Propagated $propagated field entries through hierarchy")

        // Build class → {yarnMethodName → srgMethodName} for inherited method resolution
        val mojangClassMethods = mutableMapOf<String, MutableMap<String, String>>()
        var mojangMethodHits = 0
        for ((obfKey, yarnMethod) in methodObfKeyToYarn) {
            val srgMethod = srgMethodByKey[obfKey] ?: continue
            val obfClass = obfKey.substringBefore('|')
            val mojangClass = mojangClassByObf[obfClass] ?: continue
            if (yarnMethod != srgMethod) {
                mojangClassMethods.getOrPut(mojangClass) { mutableMapOf() }[yarnMethod] = srgMethod
                val yarnClass = obfToYarnClass[obfClass]
                if (yarnClass != null && yarnClass != mojangClass) {
                    mojangClassMethods.getOrPut(yarnClass) { mutableMapOf() }[yarnMethod] = srgMethod
                }
                mojangMethodHits++
            }
        }
        logger.lifecycle("mojangClassMethods: ${mojangClassMethods.size} classes, $mojangMethodHits total method entries")

        // Build a flat yarnMethodName→srgMethodName map for all unambiguous method renames
        // This is used for inherited method resolution across all classes
        val yarnMethodToSrgFlat = mutableMapOf<String, String>()
        for ((_, methods) in mojangClassMethods) {
            for ((yarnName, srgName) in methods) {
                val existing = yarnMethodToSrgFlat[yarnName]
                if (existing == null) {
                    yarnMethodToSrgFlat[yarnName] = srgName
                } else if (existing != srgName) {
                    // Ambiguous — remove it
                    yarnMethodToSrgFlat.remove(yarnName)
                }
            }
        }
        logger.lifecycle("yarnMethodToSrgFlat: ${yarnMethodToSrgFlat.size} unambiguous entries")

        // Apply SRG replacements to ALL class files and downgrade bytecode to Java 17
        var patchedCount = 0
        var downgradedCount = 0
        unpacked.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val original = classFile.readBytes()
            val patched = patchClassConstantPool(original, pathQualified, mergedBareNames, mcClassInternalNames, mojangClassFields)
            // Downgrade class file version from Java 25 (69) to Java 17 (61)
            val final = if (patched.size >= 8) {
                val major = ((patched[6].toInt() and 0xFF) shl 8) or (patched[7].toInt() and 0xFF)
                if (major > 61) {
                    downgradedCount++
                    patched.copyOf().also { it[6] = 0; it[7] = 61 }
                } else patched
            } else patched
            if (!final.contentEquals(original)) {
                classFile.writeBytes(final)
                patchedCount++
            }
        }
        logger.lifecycle("Patched $patchedCount class files with SRG names, downgraded $downgradedCount bytecode versions")

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
    mojangClassFields: Map<String, Map<String, String>> = emptyMap()
): ByteArray {
    if (classBytes.size < 10) return classBytes

    var pos = 0
    pos += 4 // magic
    pos += 2 // minor
    pos += 2 // major

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
            3 -> { scanPos += 4 } // Integer
            4 -> { scanPos += 4 } // Float
            5 -> { scanPos += 8; i++ } // Long
            6 -> { scanPos += 8; i++ } // Double
            7 -> {
                val nameIdx = readU2(classBytes, scanPos)
                classRefs[i] = nameIdx
                scanPos += 2
            }
            8 -> { scanPos += 2 } // String
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
            15 -> { scanPos += 3 } // MethodHandle
            16 -> { scanPos += 2 } // MethodType
            17 -> { scanPos += 4 } // Dynamic
            18 -> { scanPos += 4 } // InvokeDynamic
            19 -> { scanPos += 2 } // Module
            20 -> { scanPos += 2 } // Package
            else -> break
        }
        i++
    }

    if (!anyPathMatch && !anyBareMatch && mojangClassFields.isEmpty()) return classBytes

    // Split bareNames into: intermediary (method_NNN, field_NNN) for substring matching,
    // and regular bare names for exact matching with safety checks
    val intermediaryPattern = Regex("^(method_|field_)\\d+$")
    val intermediaryBare = bareNames.filter { intermediaryPattern.matches(it.key) }
    val regularBareNames = bareNames.filter { !intermediaryPattern.matches(it.key) }
    val effectivePathQualified = (pathQualified + intermediaryBare).sortedByDescending { it.key.length }

    val safeBareNameUtf8Positions = mutableSetOf<Int>()

    // Determine the current class name so we also allow Fieldrefs on 'this' (inherited fields)
    val thisClassName = run {
        val thisClassIdx = readU2(classBytes, scanPos + 2) // this_class is right after access_flags
        val nameUtf8Idx = classRefs[thisClassIdx]
        if (nameUtf8Idx != null) {
            val listIdx = utf8Indices[nameUtf8Idx]
            if (listIdx != null) utf8Entries[listIdx].value else null
        } else null
    }

    if (bareNames.isNotEmpty()) {
        val bareNameValues = bareNames.map { it.key }.toSet()
        // Allow both MC classes and the current mixin class (for inherited fields like width)
        val allowedClasses = mcClassNames + setOfNotNull(thisClassName)
        // Log key bare names for debugging
        val debugNames = setOf("width", "height", "getConnection")
        val foundDebug = bareNameValues.intersect(debugNames)
        if (foundDebug.isNotEmpty()) {
            logger.lifecycle("  Bare name debug: thisClass=$thisClassName, found=$foundDebug, allowedClasses contains thisClass=${thisClassName in allowedClasses}")
        }

        for ((listIdx, entry) in utf8Entries.withIndex()) {
            if (entry.value !in bareNameValues) continue
            val utf8CpIndex = entry.cpIndex

            val referencingNATs = nameAndTypeRefs.filter { (_, nameDesc) -> nameDesc.first == utf8CpIndex }
            if (referencingNATs.isEmpty()) continue

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
                            if (className !in allowedClasses) {
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

        // Also scan own field_info and method_info tables: @Shadow members with MC-typed
        // descriptors (or primitive descriptors for fields like width:int)
        run {
            var fp = scanPos
            if (fp + 8 <= classBytes.size) {
                fp += 2 // access_flags
                fp += 2 // this_class
                fp += 2 // super_class
                val ifcCnt = readU2(classBytes, fp); fp += 2
                fp += ifcCnt * 2
                // Helper: scan fields or methods for bare names
                fun scanMembers(count: Int, isMethod: Boolean) {
                    for (mi in 0 until count) {
                        if (fp + 8 > classBytes.size) break
                        fp += 2 // access_flags
                        val mNameIdx = readU2(classBytes, fp); fp += 2
                        val mDescIdx = readU2(classBytes, fp); fp += 2
                        val mAttrCnt = readU2(classBytes, fp); fp += 2
                        for (ai in 0 until mAttrCnt) {
                            if (fp + 6 > classBytes.size) break
                            fp += 2 // attr_name_idx
                            val attrLen = ((classBytes[fp].toInt() and 0xFF) shl 24) or
                                ((classBytes[fp + 1].toInt() and 0xFF) shl 16) or
                                ((classBytes[fp + 2].toInt() and 0xFF) shl 8) or
                                (classBytes[fp + 3].toInt() and 0xFF)
                            fp += 4 + attrLen
                        }
                        val mnListIdx = utf8Indices[mNameIdx] ?: continue
                        val mdListIdx = utf8Indices[mDescIdx] ?: continue
                        val mnVal = utf8Entries[mnListIdx].value
                        if (mnVal !in bareNameValues) continue
                        val mdVal = utf8Entries[mdListIdx].value
                        // Check if descriptor references any MC class
                        val mcTypes = Regex("L([^;]+);").findAll(mdVal).map { m ->
                            m.groupValues[1]
                        }.toList()
                        val hasMcType = mcTypes.any { it in mcClassNames }
                        if (hasMcType) {
                            safeBareNameUtf8Positions.add(utf8Entries[mnListIdx].dataPos)
                            logger.lifecycle("  @Shadow-safe: $mnVal (desc=$mdVal)")
                        } else if (!isMethod && !mdVal.contains("L")) {
                            // Primitive-typed field in mixin class with bare name → likely @Shadow
                            safeBareNameUtf8Positions.add(utf8Entries[mnListIdx].dataPos)
                            logger.lifecycle("  @Shadow-safe: $mnVal (primitive desc=$mdVal)")
                        }
                    }
                }
                if (fp + 2 <= classBytes.size) {
                    val fldCnt = readU2(classBytes, fp); fp += 2
                    scanMembers(fldCnt, false)
                }
                if (fp + 2 <= classBytes.size) {
                    val mthCnt = readU2(classBytes, fp); fp += 2
                    scanMembers(mthCnt, true)
                }
            }
        }
    }

    // Resolve inherited fields: for Fieldrefs on the current class, look up parent class
    val inheritedBareReplacements = mutableMapOf<String, String>()
    if (mojangClassFields.isNotEmpty()) {
        val bareNameValues = bareNames.map { it.key }.toSet()
        // Get direct parent class name
        val parentClassIdx = readU2(classBytes, scanPos + 4) // super_class
        val parentNameUtf8Idx = classRefs[parentClassIdx]
        if (parentNameUtf8Idx != null) {
            val parentListIdx = utf8Indices[parentNameUtf8Idx]
            if (parentListIdx != null) {
                val parentClassName = utf8Entries[parentListIdx].value
                val parentFields = mojangClassFields[parentClassName]
                if (parentFields != null && parentFields.isNotEmpty()) {
                    for ((listIdx, entry) in utf8Entries.withIndex()) {
                        if (entry.value in bareNameValues) continue
                        val utf8CpIndex = entry.cpIndex
                        val srgName = parentFields[entry.value] ?: continue
                        val referencingNATs = nameAndTypeRefs.filter { (_, nameDesc) -> nameDesc.first == utf8CpIndex }
                        for ((natCpIndex, _) in referencingNATs) {
                            val refs = methodFieldRefs.filter { (_, classNat) -> classNat.second == natCpIndex }
                            for ((_, classNatPair) in refs) {
                                val classCpIndex = classNatPair.first
                                val classNameUtf8Index = classRefs[classCpIndex]
                                if (classNameUtf8Index != null) {
                                    val cnListIdx = utf8Indices[classNameUtf8Index]
                                    if (cnListIdx != null) {
                                        val className = utf8Entries[cnListIdx].value
                                        if (className == thisClassName) {
                                            inheritedBareReplacements[entry.value] = srgName
                                            safeBareNameUtf8Positions.add(entry.dataPos)
                                            logger.lifecycle("  Inherited-safe: ${entry.value} -> $srgName (parent=$parentClassName)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val newEntries = mutableMapOf<Int, Pair<String, ByteArray>>()
    var totalSizeDelta = 0

    for (entry in utf8Entries) {
        val original = entry.value
        data class ReplacementRange(val start: Int, val end: Int, val replacement: String)
        val ranges = mutableListOf<ReplacementRange>()

        for ((old, new_) in effectivePathQualified) {
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
            for ((old, new_) in regularBareNames) {
                if (original == old) {
                    ranges.add(ReplacementRange(0, old.length, new_))
                    break
                }
            }
            // Also check inherited bare replacements
            if (ranges.isEmpty()) {
                val inheritedNew = inheritedBareReplacements[original]
                if (inheritedNew != null) {
                    ranges.add(ReplacementRange(0, original.length, inheritedNew))
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
