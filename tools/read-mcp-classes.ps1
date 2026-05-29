Add-Type -AssemblyName System.IO.Compression.FileSystem
$srcJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)

# Get GuiScreen.java to see its class hierarchy and methods
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiScreen.java" }
if ($entry) {
    $stream = $entry.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
    
    # Extract class declaration and key methods
    $lines = $content -split "`n"
    Write-Host "=== GuiScreen class declaration and key methods ==="
    for ($i = 0; $i -lt $lines.Count -and $i -lt 60; $i++) {
        if ($lines[$i] -match 'class |extends |implements |public.*void|protected.*void|drawButton|drawScreen|initGui|renderTooltip|blit|drawRect|drawCentered|drawString|drawHoveringText|close|keyPress|onClose|removed|doesGuiPause|shouldPause|mouseClicked|mouseReleased|mouseDragged') {
            Write-Host "L$($i+1): $($lines[$i].Trim())"
        }
    }
    Write-Host "`n=== Methods containing 'blit' or 'drawRect' ==="
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'blit|drawRect|drawCentered') {
            Write-Host "L$($i+1): $($lines[$i].Trim())"
        }
    }
}

# Get GuiButton.java for button structure
$entry2 = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiButton.java" }
if ($entry2) {
    $stream = $entry2.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
    
    $lines = $content -split "`n"
    Write-Host "`n=== GuiButton class ==="
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'class |extends |implements |public.*void|protected.*void|drawButton|mousePressed|isHovered|render|draw|enabled|visible|packedFGColor') {
            Write-Host "L$($i+1): $($lines[$i].Trim())"
        }
    }
}

# Get IToast.java
$entry3 = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/toasts/IToast.java" }
if ($entry3) {
    $stream = $entry3.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "`n=== IToast.java ==="
    Write-Host $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
}

# Get GuiToast.java
$entry4 = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/toasts/GuiToast.java" }
if ($entry4) {
    $stream = $entry4.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "`n=== GuiToast.java ==="
    Write-Host $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
}

# Get GuiListExtended.java for list widget structure
$entry5 = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiListExtended.java" }
if ($entry5) {
    $stream = $entry5.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "`n=== GuiListExtended.java ==="
    Write-Host $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
}

$z.Dispose()
