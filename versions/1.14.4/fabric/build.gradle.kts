plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF Fabric 1.14.4 entry point"

dependencies {
    implementation(project(":versions:1.14.4:common"))

    "minecraft"(libs.minecraft1144)
    "mappings"(libs.yarn1144)

    implementation(libs.fabric.loader1144)
    implementation(libs.fabric.api1144)

    include(libs.slf4j.api)
    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val commonJar = project(":common").tasks.named<Jar>("jar")
val verCommonJar = project(":versions:1.14.4:common").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
        // Exclude classes provided by authlib 2.x (MC 1.14.4 runtime) to avoid
        // class loading conflicts. Friends-related classes are NOT in authlib 2.x.
        exclude("com/mojang/authlib/Agent.class")
        exclude("com/mojang/authlib/AuthenticationService.class")
        exclude("com/mojang/authlib/BaseAuthenticationService.class")
        exclude("com/mojang/authlib/BaseUserAuthentication.class")
        exclude("com/mojang/authlib/GameProfile*.class")
        exclude("com/mojang/authlib/GameProfileRepository.class")
        exclude("com/mojang/authlib/HttpAuthenticationService.class")
        exclude("com/mojang/authlib/HttpUserAuthentication.class")
        exclude("com/mojang/authlib/ProfileLookupCallback.class")
        exclude("com/mojang/authlib/UserAuthentication.class")
        exclude("com/mojang/authlib/UserType.class")
        exclude("com/mojang/authlib/exceptions/**")
        exclude("com/mojang/authlib/legacy/**")
        exclude("com/mojang/authlib/minecraft/**")
        exclude("com/mojang/authlib/properties/**")
        exclude("com/mojang/authlib/yggdrasil/ProfileIncompleteException.class")
        exclude("com/mojang/authlib/yggdrasil/ProfileNotFoundException.class")
        exclude("com/mojang/authlib/yggdrasil/ServicesKeyInfo.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilGameProfileRepository.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilServicesKeyInfo.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilUserAuthentication*.class")
        exclude("com/mojang/authlib/yggdrasil/request/AuthenticationRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/JoinMinecraftServerRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/RefreshRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/ValidateRequest*")
        exclude("com/mojang/authlib/yggdrasil/response/AuthenticationResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/ErrorResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/HasJoinedMinecraftServerResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftProfilePropertiesResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftTexturesPayload*")
        exclude("com/mojang/authlib/yggdrasil/response/ProfileSearchResultsResponse*")
    }
    from(verCommonJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(sourceSets.main.get().output)
}
