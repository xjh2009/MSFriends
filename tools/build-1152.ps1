# Build 1.15.2 Forge
Set-Location "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

$content = @"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://repo.spongepowered.org/maven/") }
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
    }
}
rootProject.name = "MSF"
include(":common")
include(":versions:1.21.1:common")
include(":versions:1.21.1:fabric")
include(":versions:1.21.1:neoforge")
include(":versions:1.21.11:common")
include(":versions:1.21.11:fabric")
include(":versions:1.21.11:neoforge")
include(":versions:26.1.2:common")
include(":versions:26.1.2:fabric")
include(":versions:26.1.2:neoforge")
include(":versions:1.20.1:common")
include(":versions:1.20.1:fabric")
include(":versions:1.20.1:forge")
include(":versions:1.19.2:common")
include(":versions:1.19.2:fabric")
include(":versions:1.19.2:forge")
include(":versions:1.18.2:common")
include(":versions:1.18.2:fabric")
include(":versions:1.18.2:forge")
include(":versions:1.17.1:common")
include(":versions:1.17.1:fabric")
include(":versions:1.17.1:forge")
include(":versions:1.16.5:common")
include(":versions:1.16.5:fabric")
include(":versions:1.15.2:common")
include(":versions:1.15.2:fabric")
include(":versions:1.15.2:forge")
"@
[System.IO.File]::WriteAllText("settings.gradle.kts", $content, [System.Text.UTF8Encoding]::new($false))

# Make read-only
$f = Get-Item "settings.gradle.kts"
$f.IsReadOnly = $true
Write-Host "Settings written and made read-only"

# Build
Write-Host "Building 1.15.2 Forge..."
& .\gradlew :versions:1.15.2:forge:compileJava --no-daemon 2>&1 | Select-Object -Last 100
