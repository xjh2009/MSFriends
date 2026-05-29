Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check the forge userdev config.json for tool definitions
$udJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.13.2-25.0.223\forge-1.13.2-25.0.223-userdev.jar"
$zip = [System.IO.Compression.ZipFile]::OpenRead($udJar)
$configEntry = $zip.Entries | Where-Object { $_.Name -eq "config.json" }
$stream = $configEntry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$configText = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
$zip.Dispose()

# Parse JSON (PowerShell 5 compatible)
$config = $configText | ConvertFrom-Json

Write-Host "=== Executables (Tools) ==="
$config.executables.PSObject.Properties | ForEach-Object {
    Write-Host "  $($_.Name): $($_.Value)"
}
