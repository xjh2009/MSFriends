# Isolated build script for 1.15.2 Forge
$ErrorActionPreference = "Continue"
Set-Location "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

# Kill all Java first
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Write settings
$content = Get-Content "settings-1152-backup.gradle.kts" -Raw -ErrorAction SilentlyContinue
if ($content) {
    [System.IO.File]::WriteAllText("settings.gradle.kts", $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Settings restored from backup"
} else {
    Write-Host "ERROR: No backup file found!"
    exit 1
}

# Verify
$check = Get-Content "settings.gradle.kts" -Raw
if ($check -match '1\.15\.2:forge') { 
    Write-Host "OK: 1.15.2:forge is included" 
} else { 
    Write-Host "FAIL: 1.15.2:forge not in settings" 
    exit 1 
}

# Use isolated Gradle home
$env:GRADLE_USER_HOME = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\.gradle-1152"
if (!(Test-Path $env:GRADLE_USER_HOME)) {
    New-Item -Path $env:GRADLE_USER_HOME -ItemType Directory -Force | Out-Null
}
New-Item -Path "$env:GRADLE_USER_HOME\wrapper" -ItemType Directory -Force | Out-Null
Copy-Item "gradle\wrapper\*" "$env:GRADLE_USER_HOME\wrapper\" -Force
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"

# Run build
Write-Host "Starting Gradle build..."
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-25"
& .\gradlew :versions:1.15.2:forge:compileJava --no-daemon 2>&1
Write-Host "Exit code: $LASTEXITCODE"
