Add-Type -AssemblyName System.IO.Compression.FileSystem
$commonJar = 'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\common\libs\common-0.1.0+26.1.2.jar'
$z = [System.IO.Compression.ZipFile]::OpenRead($commonJar)

# List all MinecraftBridge classes
$entries = $z.Entries | Where-Object { $_.FullName -like 'dev/msf/friends/bridge/MinecraftBridge*' }
foreach ($e in $entries) {
    Write-Host "=== $($e.FullName) ==="
    $stream = $e.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    # Can't decompile bytecode, but list the entries
    Write-Host "  Size: $($e.Length) bytes"
    $reader.Close()
    $stream.Close()
}

# Also list all common JAR entries for reference
Write-Host ""
Write-Host "=== All dev.msf.friends classes ==="
$z.Entries | Where-Object { $_.FullName -like 'dev/msf/friends/*.class' -and $_.FullName.Split('/').Length -eq 4 } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== All webrtc classes ==="
$z.Entries | Where-Object { $_.FullName -like 'dev/msf/friends/webrtc/*.class' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== HeadlessMinecraftBridge companion classes ==="
$z.Entries | Where-Object { $_.FullName -like 'dev/msf/friends/bridge/HeadlessMinecraftBridge*' } | ForEach-Object { Write-Host $_.FullName }

$z.Dispose()
