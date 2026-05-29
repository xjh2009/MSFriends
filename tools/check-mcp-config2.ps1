Add-Type -AssemblyName System.IO.Compression.FileSystem

$mcpZip = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_config\1.13.2-20190213.203750\mcp_config-1.13.2-20190213.203750.zip"
if (-not (Test-Path $mcpZip)) {
    Write-Host "File not found: $mcpZip"
    exit
}

Write-Host "MCP Config: $((Get-Item $mcpZip).Length) bytes"
$zip = [System.IO.Compression.ZipFile]::OpenRead($mcpZip)
Write-Host "Entries: $($zip.Entries.Count)"

foreach ($entry in $zip.Entries) {
    Write-Host "  $($entry.Length) $($entry.FullName)"
    if ($entry.Name -match '\.jar$') {
        $tempFile = Join-Path $env:TEMP "mcp_tool_$($entry.Name)"
        try {
            $stream = $entry.Open()
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
}

$zip.Dispose()
Write-Host "Done"
