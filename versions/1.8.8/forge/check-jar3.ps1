Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check forgeBin jar for ALL classes
$f = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\stable\22\forgeBin-1.8.9-11.15.1.2318-1.8.9.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($f)
Write-Host "=== forgeBin top-level packages ==="
$z.Entries | ForEach-Object { 
    $parts = $_.FullName.Split('/')
    if ($parts.Length -ge 2) { $parts[0] + '/' + $parts[1] }
} | Sort-Object -Unique | ForEach-Object { Write-Host $_ }
Write-Host ""

Write-Host "=== Looking for FML-like entries ==="
$z.Entries | Where-Object { $_.FullName -like '*fml*' -or $_.FullName -like '*forge*' -or $_.FullName -like '*cpw*' } | ForEach-Object { Write-Host $_.FullName } | Select-Object -First 20

Write-Host ""
Write-Host "=== Allnet.minecraft entries ==="
$z.Entries | Where-Object { $_.FullName -like 'net/minecraft/*' -and $_.FullName -like '*.class' -and $_.FullName.Split('/').Length -eq 4 } | ForEach-Object { Write-Host $_.FullName } | Select-Object -First 30
$z.Dispose()

# Check broader Forge caches for FML jars
Write-Host ""
Write-Host "=== Forge library cache ==="
$libDir = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9"
Get-ChildItem $libDir -Recurse -File | ForEach-Object { Write-Host "$($_.Length)  $($_.FullName)" }
