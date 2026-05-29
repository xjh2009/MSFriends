Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = "$env:USERPROFILE\.gradle\caches\forgegradle"

# List ALL files in the freshly downloaded cache
Write-Host "=== ALL files in forgegradle cache ==="
Get-ChildItem $base -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.PSIsContainer) {
        Write-Host "DIR: $($_.FullName)"
    } else {
        Write-Host "FILE: $($_.Length) $($_.FullName)"
    }
}

Write-Host "`nDone"
