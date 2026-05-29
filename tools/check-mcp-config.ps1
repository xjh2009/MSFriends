Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\.gradle\mavenizer\repo\net\minecraft\mcp_config\1.13.2-20190213.203750\mcp_config-1.13.2-20190213.203750.zip"
if (Test-Path $zipPath) {
    Write-Host "Zip exists: $(Get-Item $zipPath | Select-Object Length)"
    $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    $entries = $zip.Entries
    Write-Host "Total entries: $($entries.Count)"
    foreach ($e in $entries) {
        Write-Host "  $($e.Length) $($e.FullName)"
    }
    $jarEntries = $entries | Where-Object { $_.Name -match '\.jar$' }
    Write-Host "`nJar entries: $($jarEntries.Count)"
    foreach ($j in $jarEntries) {
        Write-Host "  $($j.Length) $($j.FullName)"
        # Extract to temp and validate
        $tempPath = [System.IO.Path]::GetTempFileName() + ".jar"
        try {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($j, $tempPath, $true)
            $testZip = [System.IO.Compression.ZipFile]::OpenRead($tempPath)
            $testZip.Dispose()
            Write-Host "    -> VALID"
        } catch {
            Write-Host "    -> CORRUPTED: $_"
        } finally {
            Remove-Item $tempPath -ErrorAction SilentlyContinue
        }
    }
    $zip.Dispose()
} else {
    Write-Host "File not found"
}
