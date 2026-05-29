Add-Type -AssemblyName System.IO.Compression.FileSystem

# Look for ALL files in the forge 1.13.2 snapshot cache directory
$snapshotDir = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13"
Write-Host "=== Snapshot directory contents ==="
Get-ChildItem $snapshotDir -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -match '\.jar$') {
        $valid = "VALID"
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
            $z.Dispose()
        } catch {
            $valid = "CORRUPTED"
        }
        Write-Host "$valid $($_.Length) $($_.FullName)"
    } else {
        Write-Host "FILE $($_.Length) $($_.FullName)"
    }
}

# Check the forge-level dir
$forgeLevel = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223"
Write-Host "`n=== Forge-level directory ==="
Get-ChildItem $forgeLevel -File -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -match '\.jar$') {
        $valid = "VALID"
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
            $z.Dispose()
        } catch {
            $valid = "CORRUPTED"
        }
        Write-Host "$valid $($_.Length) $($_.FullName)"
    } else {
        Write-Host "FILE $($_.Length) $($_.FullName)"
    }
}

# Check the forge-1.13.2-sources.jar specifically
$sourcesJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.13.2-25.0.223\forge-1.13.2-25.0.223-sources.jar"
Write-Host "`n=== Forge sources jar ==="
if (Test-Path $sourcesJar) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($sourcesJar)
        Write-Host "VALID $($z.Entries.Count) entries"
        $z.Dispose()
    } catch {
        Write-Host "CORRUPTED: $($_.Exception.Message)"
    }
}

# Check the injected-sources.jar
$injJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
Write-Host "`n=== Injected-sources jar ==="
if (Test-Path $injJar) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($injJar)
        Write-Host "VALID $($z.Entries.Count) entries"
        $z.Dispose()
    } catch {
        Write-Host "CORRUPTED: $($_.Exception.Message)"
    }
}
