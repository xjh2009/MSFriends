$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$settings = Get-Content "settings-1171-forge.gradle.kts" -Raw
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText("settings.gradle.kts", $settings, $utf8)

# Verify settings was written correctly
$verify = Get-Content "settings.gradle.kts" -First 3
Write-Host "Settings start: $($verify -join ' | ')"

# Run gradle
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
& .\gradlew.bat :versions:1.17.1:forge:relocateFatJar --no-daemon --console=plain 2>&1 | Out-File "build-1171-rebuild4.txt" -Encoding utf8

# Show results
Select-String -Path "build-1171-rebuild4.txt" -Pattern "Inherited|@Shadow-safe|Patched|FAILED|SUCCESSFUL" | ForEach-Object { $_.Line }
