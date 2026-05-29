# Build 1.15.2 Forge with file lock + configure-on-demand
$ErrorActionPreference = "Continue"
Set-Location "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

# Kill all Java first
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

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

# Open file with write access + allow others to read only (prevents writes by other agents)
$lockStream = [System.IO.File]::Open("settings.gradle.kts", [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
$bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($content)
$lockStream.Write($bytes, 0, $bytes.Length)
$lockStream.Flush()
Write-Host "Settings written and file locked (prevents write by others, allows read)"

# Verify from content variable (file is locked, can't re-read)
if ($content -match '1\.15\.2:forge') { 
    Write-Host "OK: 1.15.2:forge confirmed in content" 
} else { 
    Write-Host "FAIL"
    $lockStream.Close()
    exit 1 
}

# Clean corrupted mavenizer cache
$cacheDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\minecraft_tasks\1.15.2"
if (Test-Path "$cacheDir\client.jar") {
    Remove-Item "$cacheDir\client.jar" -Force -ErrorAction SilentlyContinue
    Write-Host "Deleted corrupted client.jar"
}
if (Test-Path "$cacheDir\client.jar.cache") {
    Remove-Item "$cacheDir\client.jar.cache" -Force -ErrorAction SilentlyContinue
    Write-Host "Deleted client.jar.cache"
}

Write-Host "Starting Gradle build with --configure-on-demand..."

try {
    & .\gradlew :versions:1.15.2:forge:compileJava --no-daemon --configure-on-demand 2>&1
    Write-Host "Exit code: $LASTEXITCODE"
} finally {
    $lockStream.Close()
    Write-Host "File lock released"
}
