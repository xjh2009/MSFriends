Add-Type -AssemblyName System.IO.Compression.FileSystem

$mcpZip = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\de\oceanlabs\mcp\mcp_config\1.13.2-20190213.203750\mcp_config-1.13.2-20190213.203750.zip"
$zip = [System.IO.Compression.ZipFile]::OpenRead($mcpZip)
$configEntry = $zip.Entries | Where-Object { $_.Name -eq "config.json" }
$stream = $configEntry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$configText = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
$zip.Dispose()

Write-Host $configText
