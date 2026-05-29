Add-Type -AssemblyName System.IO.Compression.FileSystem

# Find the forge userdev jar  
$forgeDir = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.13.2-25.0.223"
Write-Host "=== Forge 1.13.2 dir ==="
Get-ChildItem $forgeDir -Recurse -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.Length) $($_.FullName)" 
}

# Check the userdev jar for embedded tool jars
$udJar = Join-Path $forgeDir "forge-1.13.2-25.0.223-userdev.jar"
if (Test-Path $udJar) {
    Write-Host "`n=== Checking forge userdev jar ==="
    $zip = [System.IO.Compression.ZipFile]::OpenRead($udJar)
    $jarEntries = $zip.Entries | Where-Object { $_.Name -match '\.jar$' }
    Write-Host "Embedded jars: $($jarEntries.Count)"
    foreach ($j in $jarEntries) {
        Write-Host "  $($j.Length) $($j.FullName)"
        # Extract and validate
        $tempFile = Join-Path $env:TEMP "forge_inner_$($j.Name)"
        try {
            $stream = $j.Open()
            $fs = [System.IO.File]::Create($tempFile)
            $stream.CopyTo($fs)
            $fs.Close()
            $stream.Close()
            $testZip = [System.IO.Compression.ZipFile]::OpenRead($tempFile)
            $testZip.Dispose()
            Write-Host "    -> VALID"
        } catch {
            Write-Host "    -> CORRUPTED: $($_.Exception.Message)"
        } finally {
            Remove-Item $tempFile -ErrorAction SilentlyContinue
        }
    }
    $zip.Dispose()
}

# Check the mcp_config zip too
$mcpZip = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_config\1.13.2-20190213.203750\mcp_config-1.13.2-20190213.203750.zip"
if (Test-Path $mcpZip) {
    Write-Host "`n=== Checking MCP config zip ==="
    $zip = [System.IO.Compression.ZipFile]::OpenRead($mcpZip)
    foreach ($entry in $zip.Entries) {
        Write-Host "  $($entry.Length) $($entry.FullName)"
    }
    $zip.Dispose()
}
