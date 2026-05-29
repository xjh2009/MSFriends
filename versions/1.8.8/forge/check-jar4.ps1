Add-Type -AssemblyName System.IO.Compression.FileSystem
$f = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\stable\22\forgeBin-1.8.9-11.15.1.2318-1.8.9.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($f)

Write-Host "=== net.minecraftforge.fml.common.Mod ==="
$z.Entries | Where-Object { $_.FullName -eq 'net/minecraftforge/fml/common/Mod.class' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== IFMLLoadingPlugin ==="
$z.Entries | Where-Object { $_.FullName -like '*IFMLLoadingPlugin*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== ClientRegistry ==="
$z.Entries | Where-Object { $_.FullName -like '*ClientRegistry*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== FMLPre/FMLInit ==="
$z.Entries | Where-Object { $_.FullName -like '*FMLPre*Event*' -or $_.FullName -like '*FMLInit*Event*' -or $_.FullName -like '*FMLPost*Event*' -or $_.FullName -like '*FMLServerStarted*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== SubscribeEvent ==="
$z.Entries | Where-Object { $_.FullName -like '*SubscribeEvent*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== InputEvent ==="
$z.Entries | Where-Object { $_.FullName -like '*gameevent/InputEvent*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== Session ==="
$z.Entries | Where-Object { $_.FullName -eq 'net/minecraft/util/Session.class' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== FMLNetworkEvent ==="
$z.Entries | Where-Object { $_.FullName -like '*FMLNetworkEvent*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== NetworkManager field check ==="
Write-Host "  LoginServerState fields:"
$z.Entries | Where-Object { $_.FullName -like 'net/minecraft/server/network/NetHandlerLoginServer*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== GuiTextField/GuiButton ==="
$z.Entries | Where-Object { $_.FullName -eq 'net/minecraft/client/gui/GuiTextField.class' -or $_.FullName -eq 'net/minecraft/client/gui/GuiButton.class' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== NetworkSystem entries ==="
$z.Entries | Where-Object { $_.FullName -like 'net/minecraft/network/NetworkSystem*' } | ForEach-Object { Write-Host $_.FullName }

Write-Host ""
Write-Host "=== Keys ==="
$z.Entries | Where-Object { $_.FullName -like '*KeyBinding*' } | ForEach-Object { Write-Host $_.FullName }

$z.Dispose()
