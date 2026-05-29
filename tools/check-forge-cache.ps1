# Check the forge-specific cache directory
$forgeCache = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge"
Write-Host "=== Forge cache structure ==="
Get-ChildItem $forgeCache -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "1.13.2" -and $_.Name -match '\.jar$' } | ForEach-Object {
    $valid = "VALID"
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        $valid = "CORRUPTED"
    }
    Write-Host "$valid $($_.Length) $($_.FullName)"
}

# Check if there are any 0-byte or tiny jar files
Write-Host "`n=== Suspiciously small jar files ==="
Get-ChildItem $forgeCache -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '\.jar$' -and $_.Length -lt 50 } | ForEach-Object {
    Write-Host "TINY $($_.Length) $($_.FullName)"
}

# Also look for lock files or incomplete files
Write-Host "`n=== .lock files ==="
Get-ChildItem $forgeCache -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '\.lock$' } | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}

# Check recompiled.jar specifically
Write-Host "`n=== Looking for recompiled.jar ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge" -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'recompiled' -and $_.FullName -match '1.13.2' } | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}

# Look for ANY files that are small and end in .jar in the forge cache for 1.13.2
Write-Host "`n=== All .jar files under forge 1.13.2 ==="
Get-ChildItem $forgeCache -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "1\.13\.2|25\.0\.223" } | Where-Object { $_.Name -match '\.jar$' } | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}
