Add-Type -AssemblyName System.IO.Compression.FileSystem
$f = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\stable\22\forgeBin-1.8.9-11.15.1.2318-1.8.9.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($f)

Write-Host "=== NetHandlerHandshakeTCP ==="
$z.Entries | Where-Object { $_.FullName -like '*NetHandlerHandshakeTCP*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== NetHandlerLoginClient ==="
$z.Entries | Where-Object { $_.FullName -like '*NetHandlerLoginClient*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== C00PacketLoginStart ==="
$z.Entries | Where-Object { $_.FullName -like '*C00PacketLoginStart*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== ServerData ==="
$z.Entries | Where-Object { $_.FullName -like '*ServerData*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== RenderGameOverlayEvent ==="
$z.Entries | Where-Object { $_.FullName -like '*RenderGameOverlayEvent*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== PlayerSocialManager ==="
# Check if PlayerSocialManager has sendFriendRequest
$commonJar = 'C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\common\libs\common-0.1.0+26.1.2.jar'
$z2 = [System.IO.Compression.ZipFile]::OpenRead($commonJar)
$z2.Entries | Where-Object { $_.FullName -like '*PlayerSocialManager*' } | ForEach-Object { Write-Host $_.FullName }
$z2.Entries | Where-Object { $_.FullName -like '*MinecraftBridge*' } | ForEach-Object { Write-Host $_.FullName }
$z2.Entries | Where-Object { $_.FullName -like '*P2PManager*' } | ForEach-Object { Write-Host $_.FullName }
$z2.Entries | Where-Object { $_.FullName -like '*RtcChannel*' } | ForEach-Object { Write-Host $_.FullName }
$z2.Entries | Where-Object { $_.FullName -like '*Logging*' } | ForEach-Object { Write-Host $_.FullName }
$z.Dispose()
$z2.Dispose()
