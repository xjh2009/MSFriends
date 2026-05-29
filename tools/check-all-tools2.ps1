Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check mcinjector
$mcijar = "$env:USERPROFILE\.gradle\caches\modules-2\metadata-2.107\descriptors\de.oceanlabs.mcp\mcinjector"
Write-Host "=== mcinjector dir ==="
Get-ChildItem $mcijar -Recurse -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }

# Search for mcinjector jar anywhere
Write-Host "`n=== mcinjector jar files ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "*mcinjector*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    $valid = "VALID"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        $valid = "CORRUPTED"
    }
    Write-Host "$valid $($_.Length) $($_.FullName)"
}

# Check forgeflower
Write-Host "`n=== forgeflower jar files ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mcp-tools\net\minecraftforge\forgeflower" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    $valid = "VALID"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        $valid = "CORRUPTED"
    }
    Write-Host "$valid $($_.Length) $($_.FullName)"
}

# Check mergetool
Write-Host "`n=== mergetool jar files ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\mergetool" -Recurse -Filter "*fatjar*" -ErrorAction SilentlyContinue | ForEach-Object {
    $valid = "VALID"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        $valid = "CORRUPTED"
    }
    Write-Host "$valid $($_.Length) $($_.FullName)"
}

# Also check SpecialSource sha1
$ssSha = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mcp-tools\net\md-5\SpecialSource\1.8.3\SpecialSource-1.8.3-shaded.jar.sha1"
if (Test-Path $ssSha) {
    Write-Host "`n=== SpecialSource SHA1 ==="
    Write-Host (Get-Content $ssSha)
    $actual = (Get-FileHash "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mcp-tools\net\md-5\SpecialSource\1.8.3\SpecialSource-1.8.3-shaded.jar" -Algorithm SHA1).Hash.ToLower()
    Write-Host "Actual: $actual"
}
