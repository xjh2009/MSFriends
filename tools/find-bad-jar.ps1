Add-Type -AssemblyName System.IO.Compression.FileSystem

# Search for all jars in the forge 1.13.2 cache area
$base = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer"
$version = "1.13.2"

Write-Host "=== All files in forgegradle/mavenizer matching 1.13.2 ==="
Get-ChildItem $base -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "1.13.2|25.0.223" } | ForEach-Object {
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

# Check the project build directory too
$buildDir = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\versions-1.13.2-forge"
if (Test-Path $buildDir) {
    Write-Host "`n=== Build dir jars ==="
    Get-ChildItem $buildDir -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
        $valid = "VALID"
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
            $z.Dispose()
        } catch {
            $valid = "CORRUPTED"
        }
        Write-Host "$valid $($_.Length) $($_.FullName)"
    }
}

# Also check the merged-decompile and min-stage for 1.13.2
$buildBase = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\build"
Get-ChildItem $buildBase -Directory | Where-Object { $_.Name -match "1.13.2|temp" } | ForEach-Object {
    Write-Host "`n=== $($_.Name) ==="
    Get-ChildItem $_.FullName -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
        $valid = "VALID"
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
            $z.Dispose()
        } catch {
            $valid = "CORRUPTED"
        }
        Write-Host "$valid $($_.Length) $($_.FullName)"
    }
}
