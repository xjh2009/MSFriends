plugins {
    alias(libs.plugins.fabric.loom)
}

description = "MSF Fabric 1.19.2 entry point"

loom {
    // Legacy mixin AP disabled — modern Fabric Loom handles mixin remapping
    // at runtime without baking intermediary names into annotations.
}

dependencies {
    implementation(project(":versions:1.19.2:common"))

    "minecraft"(libs.minecraft1192)

    // Yarn mappings for 1.19.2
    "mappings"(libs.yarn1192)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api1192)

    implementation(libs.webrtc.java)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val commonJar = project(":common").tasks.named<Jar>("jar")
val verCommonJar = project(":versions:1.19.2:common").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(commonJar, verCommonJar)
    from(commonJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
        // Exclude classes provided by authlib 3.x (MC 1.19.2 runtime) to avoid
        // IncompatibleClassChangeError: Environment is class in 3.x but interface in 7.x.
        // Friends-related classes (FriendsService, YggdrasilFriendsService, DTOs) are
        // NOT in authlib 3.x and must be kept.
        exclude("com/mojang/authlib/Agent.class")
        exclude("com/mojang/authlib/AuthenticationService.class")
        exclude("com/mojang/authlib/BaseAuthenticationService.class")
        exclude("com/mojang/authlib/BaseUserAuthentication.class")
        exclude("com/mojang/authlib/Environment*.class")
        exclude("com/mojang/authlib/EnvironmentParser.class")
        exclude("com/mojang/authlib/GameProfile*.class")
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
        exclude("com/mojang/authlib/yggdrasil/YggdrasilEnvironment*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilGameProfileRepository.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilServicesKeyInfo.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilUserApiService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilUserAuthentication*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrassilTelemetry*.class")
        exclude("com/mojang/authlib/yggdrasil/request/AbuseReportRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/AuthenticationRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/JoinMinecraftServerRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/RefreshRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/TelemetryEventsRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/ValidateRequest*")
        exclude("com/mojang/authlib/yggdrasil/response/AuthenticationResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/BlockListResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/ErrorResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/HasJoinedMinecraftServerResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/KeyPairResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftProfilePropertiesResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftTexturesPayload*")
        exclude("com/mojang/authlib/yggdrasil/response/ProfileSearchResultsResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/RefreshResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/Response*")
        exclude("com/mojang/authlib/yggdrasil/response/User.class")
        exclude("com/mojang/authlib/yggdrasil/response/UserAttributesResponse*")
        exclude("com/mojang/util/**")
    }
    from(verCommonJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
        // Same exclusions as above
        exclude("com/mojang/authlib/Agent.class")
        exclude("com/mojang/authlib/AuthenticationService.class")
        exclude("com/mojang/authlib/BaseAuthenticationService.class")
        exclude("com/mojang/authlib/BaseUserAuthentication.class")
        exclude("com/mojang/authlib/Environment*.class")
        exclude("com/mojang/authlib/EnvironmentParser.class")
        exclude("com/mojang/authlib/GameProfile*.class")
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
        exclude("com/mojang/authlib/yggdrasil/YggdrasilEnvironment*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilGameProfileRepository.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilServicesKeyInfo.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilUserApiService*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrasilUserAuthentication*.class")
        exclude("com/mojang/authlib/yggdrasil/YggdrassilTelemetry*.class")
        exclude("com/mojang/authlib/yggdrasil/request/AbuseReportRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/AuthenticationRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/JoinMinecraftServerRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/RefreshRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/TelemetryEventsRequest*")
        exclude("com/mojang/authlib/yggdrasil/request/ValidateRequest*")
        exclude("com/mojang/authlib/yggdrasil/response/AuthenticationResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/BlockListResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/ErrorResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/HasJoinedMinecraftServerResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/KeyPairResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftProfilePropertiesResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/MinecraftTexturesPayload*")
        exclude("com/mojang/authlib/yggdrasil/response/ProfileSearchResultsResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/RefreshResponse*")
        exclude("com/mojang/authlib/yggdrasil/response/Response*")
        exclude("com/mojang/authlib/yggdrasil/response/User.class")
        exclude("com/mojang/authlib/yggdrasil/response/UserAttributesResponse*")
        exclude("com/mojang/util/**")
    }
    // Bundle webrtc-java classes (exclude module-info to avoid JPMS conflicts)
    from({ configurations.runtimeClasspath.get().filter { it.name.contains("webrtc-java") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class")
    }
    manifest {
        attributes(
            "Specification-Title" to "MSF-fabric-1.19.2",
            "Specification-Version" to project.version,
            "Implementation-Title" to "MSF-fabric-1.19.2",
            "Implementation-Version" to project.version
        )
    }
}
