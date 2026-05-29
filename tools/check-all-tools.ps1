Add-Type -AssemblyName System.IO.Compression.FileSystem

# Find SpecialSource jar
$ssPath = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\md-5\SpecialSource\1.8.3"
Write-Host "=== SpecialSource dir ==="
Get-ChildItem $ssPath -Recurse -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }

# Try to open it as a zip
$ssJar = Get-ChildItem $ssPath -Recurse -Filter "*.jar" | Select-Object -First 1
if ($ssJar) {
    Write-Host "`n=== Testing SpecialSource jar ==="
    Write-Host "File: $($ssJar.FullName)"
    Write-Host "Size: $($ssJar.Length) bytes"
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($ssJar.FullName)
        Write-Host "Entries: $($zip.Entries.Count)"
        $mainEntry = $zip.Entries | Where-Object { $_.FullName -match "MANIFEST.MF" }
        if ($mainEntry) {
            $stream = $mainEntry.Open()
            $reader = New-Object System.IO.StreamReader($stream)
            Write-Host "MANIFEST:"
            Write-Host $reader.ReadToEnd()
            $reader.Close()
            $stream.Close()
        }
        $zip.Dispose()
        Write-Host "VALID jar"
    } catch {
        Write-Host "CORRUPTED: $($_.Exception.Message)"
    }
}

# Check all tool jars
Write-Host "`n=== All tool jars ==="
$tools = @("de/oceanlabs/mcp/mcinjector", "net/md-5/SpecialSource", "net/minecraftforge/forgeflower", "net/minecraftforge/mergetool")
foreach ($tool in $tools) {
    $toolDir = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\$tool"
    $jars = Get-ChildItem $toolDir -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue
    foreach ($j in $jars) {
        $valid = "VALID"
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
            $z.Dispose()
        } catch {
            $valid = "CORRUPTED"
        }
        Write-Host "$valid $($j.Length) $($j.FullName)"
    }
}
