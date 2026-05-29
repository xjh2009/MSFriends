Add-Type -AssemblyName System.IO.Compression.FileSystem

$cacheDir = "$env:USERPROFILE\.gradle\caches"
$forgeGradleDir = "$cacheDir\forgegradle"

# Find all jar files
Get-ChildItem $forgeGradleDir -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}

Write-Host "`n=== Zip files ==="
Get-ChildItem $forgeGradleDir -Recurse -Filter "*.zip" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}

Write-Host "`n=== GZ files ==="
Get-ChildItem $forgeGradleDir -Recurse -Filter "*.gz" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
}
