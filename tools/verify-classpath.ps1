Add-Type -AssemblyName System.IO.Compression.FileSystem

# Verify key class paths exist in recompiled.jar
$jarPath = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13\recompiled.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($jarPath)

$neededClasses = @(
    "net/minecraft/util/ResourceLocation.class",
    "net/minecraft/util/text/ITextComponent.class",
    "net/minecraft/util/text/TextComponentTranslation.class",
    "net/minecraft/util/text/TextComponentString.class",
    "net/minecraft/client/Minecraft.class",
    "net/minecraft/client/gui/GuiScreen.class",
    "net/minecraft/client/gui/GuiButton.class",
    "net/minecraft/client/gui/Gui.class",
    "net/minecraft/client/gui/GuiEventHandler.class",
    "net/minecraft/client/gui/FontRenderer.class",
    "net/minecraft/client/gui/IGuiEventListener.class",
    "net/minecraft/client/gui/GuiListExtended.class",
    "net/minecraft/client/gui/GuiOverlayDebug.class",
    "net/minecraft/client/gui/GuiNewChat.class",
    "net/minecraft/client/gui/toasts/IToast.class",
    "net/minecraft/client/gui/toasts/GuiToast.class",
    "net/minecraft/client/renderer/GlStateManager.class",
    "net/minecraft/client/resources/DefaultPlayerSkin.class",
    "net/minecraft/client/resources/SkinManager.class",
    "net/minecraft/client/renderer/texture/DynamicTexture.class",
    "net/minecraft/client/renderer/texture/NativeImage.class",
    "com/mojang/blaze3d/platform/GlStateManager.class"
)

Write-Host "=== Checking needed classes ==="
foreach ($class in $neededClasses) {
    $entry = $z.Entries | Where-Object { $_.FullName -eq $class }
    if ($entry) {
        Write-Host "FOUND $class ($($entry.Length) bytes)"
    } else {
        Write-Host "MISSING $class"
    }
}

# Also check the Gui class for blit/drawRect
$guiEntry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/Gui.class" }
if ($guiEntry) {
    # We can't decompile in PowerShell, but we can check the size
    Write-Host "`nGui.class size: $($guiEntry.Length) bytes"
}

# Check for ToastGui vs GuiToast
$toastGui = $z.Entries | Where-Object { $_.FullName -match "ToastGui" }
$guiToast = $z.Entries | Where-Object { $_.FullName -match "GuiToast" }
Write-Host "`nToastGui entries: $($toastGui.Count)"
$toastGui | ForEach-Object { Write-Host "  $($_.FullName)" }
Write-Host "GuiToast entries: $($guiToast.Count)"
$guiToast | ForEach-Object { Write-Host "  $($_.FullName)" }

# Check for Screen vs GuiScreen
$screen = $z.Entries | Where-Object { $_.FullName -match "/Screen\.class$" }
$guiScreen = $z.Entries | Where-Object { $_.FullName -match "/GuiScreen\.class$" }
Write-Host "`nScreen entries: $($screen.Count)"
$screen | ForEach-Object { Write-Host "  $($_.FullName)" }
Write-Host "GuiScreen entries: $($guiScreen.Count)"
$guiScreen | ForEach-Object { Write-Host "  $($_.FullName)" }

$z.Dispose()
