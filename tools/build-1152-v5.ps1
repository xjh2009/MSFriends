# Build 1.15.2 Forge using separate settings file to avoid other agent interference
$ErrorActionPreference = "Continue"
Set-Location "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

# Kill all Java first
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Clean corrupted mavenizer cache for 1.15.2 client.jar
$cacheDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\minecraft_tasks\1.15.2"
if (Test-Path "$cacheDir\client.jar") {
    Remove-Item "$cacheDir\client.jar" -Force -ErrorAction SilentlyContinue
    Write-Host "Deleted corrupted client.jar"
}
if (Test-Path "$cacheDir\client.jar.cache") {
    Remove-Item "$cacheDir\client.jar.cache" -Force -ErrorAction SilentlyContinue
    Write-Host "Deleted client.jar.cache"
}
# Also clean the mavenizer task caches entirely to force re-download
$mcTasks = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\minecraft_tasks"
if (Test-Path $mcTasks) {
    Remove-Item $mcTasks -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Cleaned minecraft_tasks cache"
}

# Verify separate settings file
if (!(Test-Path "settings-1152.gradle.kts")) {
    Write-Host "ERROR: settings-1152.gradle.kts not found!"
    exit 1
}
$check = Get-Content "settings-1152.gradle.kts" -Raw
if ($check -match '1\.15\.2:forge') { 
    Write-Host "OK: settings-1152.gradle.kts has 1.15.2:forge" 
} else { 
    Write-Host "FAIL" 
    exit 1 
}

Write-Host "Starting Gradle build with --settings-file settings-1152.gradle.kts..."
& .\gradlew --settings-file settings-1152.gradle.kts :versions:1.15.2:forge:compileJava --no-daemon 2>&1
Write-Host "Exit code: $LASTEXITCODE"
