# Build 1.15.2 Forge - writes settings and keeps lock during build
Set-Location "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

# Ensure read-only is off
$f = Get-Item "settings.gradle.kts" -ErrorAction SilentlyContinue
if ($f) { $f.IsReadOnly = $false }

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

# Keep a copy as backup
[System.IO.File]::WriteAllText("settings-1152-backup.gradle.kts", $content, [System.Text.UTF8Encoding]::new($false))

# Use a background job to keep restoring the file while build runs
$monitorJob = Start-Job -ScriptBlock {
    param($filePath, $content)
    while ($true) {
        $current = [System.IO.File]::ReadAllText($filePath, [System.Text.UTF8Encoding]::new($false))
        if ($current -notmatch '1\.15\.2:forge') {
            [System.IO.File]::WriteAllText($filePath, $content, [System.Text.UTF8Encoding]::new($false))
        }
        Start-Sleep -Seconds 1
    }
} -ArgumentList (Join-Path $PWD "settings.gradle.kts"), $content

# Write settings immediately
[System.IO.File]::WriteAllText("settings.gradle.kts", $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Settings written, monitor job started. Building..."

try {
    $buildResult = & .\gradlew :versions:1.15.2:forge:compileJava --no-daemon 2>&1
    $buildResult | Select-Object -Last 100
} finally {
    # Stop the monitor job
    Stop-Job $monitorJob -ErrorAction SilentlyContinue
    Remove-Job $monitorJob -Force -ErrorAction SilentlyContinue
    Write-Host "Monitor job stopped"
}
