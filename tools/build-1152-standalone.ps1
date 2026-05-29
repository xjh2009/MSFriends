# Standalone build for 1.15.2 Forge - completely independent of main project
$ErrorActionPreference = "Stop"
$standaloneDir = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\dev\msf-1152-forge"

# Kill all Java first
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Create standalone project structure
New-Item -Path "$standaloneDir" -ItemType Directory -Force | Out-Null

# Copy source files
$sourceDir = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.15.2\forge\src"
$destSrc = "$standaloneDir\src"
if (Test-Path $destSrc) { Remove-Item $destSrc -Recurse -Force }
Copy-Item $sourceDir $destSrc -Recurse -Force
Write-Host "Source files copied"

# Copy common source if available
$commonSrc = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\common\src"
$commonDestSrc = "$standaloneDir\common-src"
if (Test-Path $commonSrc) {
    if (Test-Path $commonDestSrc) { Remove-Item $commonDestSrc -Recurse -Force }
    Copy-Item $commonSrc $commonDestSrc -Recurse -Force
    Write-Host "Common source copied"
}

# Copy gradle wrapper
$wrapperDir = "$standaloneDir\gradle\wrapper"
New-Item -Path $wrapperDir -ItemType Directory -Force | Out-Null
Copy-Item "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\gradle\wrapper\*" $wrapperDir -Force
Copy-Item "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\gradlew" "$standaloneDir\gradlew" -Force
Copy-Item "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\gradlew.bat" "$standaloneDir\gradlew.bat" -Force

Write-Host "Standalone project created at $standaloneDir"
Write-Host "Files:"
Get-ChildItem $standaloneDir -Recurse -Name | Select-Object -First 30
