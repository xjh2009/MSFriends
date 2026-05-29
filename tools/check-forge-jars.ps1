Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check forge userdev jar
$forgeUd = "$env:USERPROFILE\.gradle\caches\forgegradle\mavenizer\repo\net\minecraftforge\forge\1.13.2-25.0.223\forge-1.13.2-25.0.223-userdev.jar"
if (Test-Path $forgeUd) {
    Write-Host "Forge userdev: $(Get-Item $forgeUd | Select-Object -ExpandProperty Length) bytes"
    $zip = [System.IO.Compression.ZipFile]::OpenRead($forgeUd)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -match '\.jar$') {
            Write-Host "  JAR: $($entry.Length) $($entry.FullName)"
            # Extract and validate
            $tempPath = Join-Path $env:TEMP "mcp_check_$($entry.Name)"
            try {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $tempPath, $true)
                $testZip = [System.IO.Compression.ZipFile]::OpenRead($tempPath)
                $testZip.Dispose()
                Write-Host "    -> VALID"
            } catch {
                Write-Host "    -> CORRUPTED: $($_.Exception.Message)"
            } finally {
                Remove-Item $tempPath -ErrorAction SilentlyContinue
            }
        }
    }
    $zip.Dispose()
} else {
    Write-Host "File not found: $forgeUd"
}

# Check mcp_config zip
$mcpConfig = "$env:USERPROFILE\.gradle\caches\forgegradle\mavenizer\repo\net\minecraft\mcp_config\1.13.2-20190213.203750\mcp_config-1.13.2-20190213.203750.zip"
if (Test-Path $mcpConfig) {
    Write-Host "`nMCP config: $(Get-Item $mcpConfig | Select-Object -ExpandProperty Length) bytes"
    $zip = [System.IO.Compression.ZipFile]::OpenRead($mcpConfig)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -match '\.jar$') {
            Write-Host "  JAR: $($entry.Length) $($entry.FullName)"
        }
    }
    $zip.Dispose()
} else {
    Write-Host "`nMCP config not found"
}
