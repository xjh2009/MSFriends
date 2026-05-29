Add-Type -AssemblyName System.IO.Compression.FileSystem
$f = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\stable\22\forgeBin-1.8.9-11.15.1.2318-1.8.9.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($f)
$classes = @(
    'cpw/mods/fml/common/Mod.class',
    'cpw/mods/fml/relauncher/IFMLLoadingPlugin.class',
    'cpw/mods/fml/client/registry/ClientRegistry.class',
    'cpw/mods/fml/common/event/FMLPreInitializationEvent.class',
    'cpw/mods/fml/common/event/FMLInitializationEvent.class',
    'cpw/mods/fml/common/eventhandler/SubscribeEvent.class',
    'cpw/mods/fml/common/gameevent/InputEvent.class',
    'cpw/mods/fml/common/network/FMLNetworkEvent.class',
    'net/minecraft/network/NetworkSystem.class',
    'net/minecraft/network/NetworkManager.class',
    'net/minecraft/network/EnumPacketDirection.class',
    'net/minecraft/network/EnumConnectionState.class',
    'net/minecraft/client/Minecraft.class',
    'net/minecraft/client/multiplayer/WorldClient.class',
    'net/minecraft/client/network/NetHandlerPlayClient.class',
    'net/minecraft/client/gui/GuiScreen.class',
    'net/minecraft/server/MinecraftServer.class'
)
foreach ($c in $classes) {
    $e = $z.GetEntry($c)
    $found = if ($e) { 'YES' } else { 'NO' }
    Write-Host "$found  $c"
}
Write-Host ""
Write-Host "--- NetworkSystem methods ---"
$z.Entries | Where-Object { $_.FullName -like 'net/minecraft/network/NetworkSystem*' } | ForEach-Object { Write-Host $_.FullName }
Write-Host ""
Write-Host "--- cpw.mods.fml.common.eventhandler ---"
$z.Entries | Where-Object { $_.FullName -like 'cpw/mods/fml/common/eventhandler/*' } | ForEach-Object { Write-Host $_.FullName }
Write-Host ""
Write-Host "--- cpw.mods.fml.common.network ---"
$z.Entries | Where-Object { $_.FullName -like 'cpw/mods/fml/common/network/*' } | ForEach-Object { Write-Host $_.FullName }
Write-Host ""
Write-Host "--- RenderGameOverlayEvent ---"
$z.Entries | Where-Object { $_.FullName -like '*RenderGameOverlay*' } | ForEach-Object { Write-Host $_.FullName }
$z.Dispose()
