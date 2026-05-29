Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = "$env:USERPROFILE\.gradle\caches"

# Find all zip/jar files that contain "rename" or "specialsource" in their path
Write-Host "=== Checking all jars in forgegradle cache ==="
Get-ChildItem "$base\forgegradle" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
        # Write-Host "OK: $($_.Length) $($_.FullName)"
    } catch {
        Write-Host "CORRUPTED JAR: $($_.Length) $($_.FullName)"
    }
}

Write-Host "`n=== Checking all zip files in forgegradle cache ==="
Get-ChildItem "$base\forgegradle" -Recurse -Filter "*.zip" -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        # Check inner jars
        foreach ($entry in $z.Entries) {
            if ($entry.FullName -match '\.jar$' -and $entry.Length -gt 100) {
                $tempFile = Join-Path $env:TEMP "inner_$($entry.Name)"
                try {
                    $stream = $entry.Open()
                    $fs = [System.IO.File]::Create($tempFile)
                    $stream.CopyTo($fs)
                    $fs.Close()
                    $stream.Close()
                    $inner = [System.IO.Compression.ZipFile]::OpenRead($tempFile)
                    $inner.Dispose()
                } catch {
                    Write-Host "CORRUPTED INNER JAR in $($_.Name): $($entry.FullName) ($($entry.Length) bytes)"
                } finally {
                    Remove-Item $tempFile -ErrorAction SilentlyContinue
                }
            }
        }
        $z.Dispose()
    } catch {
        Write-Host "CORRUPTED ZIP: $($_.Length) $($_.FullName)"
    }
}

Write-Host "`n=== Checking .gradle local cache ==="
Get-ChildItem "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\.gradle" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        Write-Host "CORRUPTED: $($_.Length) $($_.FullName)"
    }
}

Write-Host "`nDone checking"
