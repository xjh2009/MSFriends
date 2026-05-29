Add-Type -AssemblyName System.IO.Compression.FileSystem

$srcJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)

# Read GuiScreen.java - focus on method signatures and class hierarchy
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiScreen.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = $reader.ReadToEnd() -split "`n"
$reader.Close()
$stream.Close()

Write-Host "=== GuiScreen.java - key lines ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    $l = $lines[$i].Trim()
    if ($l -match 'class GuiScreen|extends|implements|public void|protected void|public boolean|drawScreen|render|initGui|init\(|mouseClicked|mouseReleased|mouseScrolled|mouseDragged|onGuiClosed|removed|doesGuiPauseGame|shouldPause|keyPressed|charTyped|renderTooltip|renderHoveringText|drawHoveringText|blit|drawString|drawCentered|drawRect|children|getEventListeners|width|height|font|this\.mc|this\.minecraft|func_') {
        Write-Host "L$($i+1): $l"
    }
}

# Read Gui.java to see blit/drawRect methods
Write-Host "`n=== Gui.java ==="
$guiEntry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/Gui.java" }
$stream = $guiEntry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$content = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
# Print class and key methods
$guiLines = $content -split "`n"
for ($i = 0; $i -lt $guiLines.Count; $i++) {
    $l = $guiLines[$i].Trim()
    if ($l -match 'class Gui|public.*void|protected.*void|blit|drawRect|drawCentered|drawString|func_') {
        Write-Host "L$($i+1): $l"
    }
}

# Read GuiEventHandler.java
Write-Host "`n=== GuiEventHandler.java ==="
$gehEntry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiEventHandler.java" }
$stream = $gehEntry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$gehContent = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
Write-Host $gehContent

# Read GuiListExtended.java
Write-Host "`n=== GuiListExtended.java (first 100 lines) ==="
$gleEntry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiListExtended.java" }
$stream = $gleEntry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$gleContent = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
$gleLines = $gleContent -split "`n"
for ($i = 0; $i -lt [Math]::Min(100, $gleLines.Count); $i++) {
    $l = $gleLines[$i].Trim()
    if ($l -match 'class|extends|implements|public|protected|abstract|getRow|getEntry|getItem|size|add|children|func_') {
        Write-Host "L$($i+1): $l"
    }
}

$z.Dispose()
