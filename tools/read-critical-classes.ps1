Add-Type -AssemblyName System.IO.Compression.FileSystem

$forgeDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223"
$srcJar = "$forgeDir\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)

# Read GuiButton.java - full source
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiButton.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
Write-Host "=== GuiButton.java ==="
for ($i = 0; $i -lt [Math]::Min(120, $lines.Count); $i++) {
    Write-Host "L$($i+1): $($lines[$i])"
}

# Read GuiYesNo.java (ConfirmScreen equivalent)
Write-Host "`n=== GuiYesNo.java (first 60 lines) ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiYesNo.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
for ($i = 0; $i -lt [Math]::Min(60, $lines.Count); $i++) {
    Write-Host "L$($i+1): $($lines[$i])"
}

# Read GuiDisconnected.java (DisconnectedScreen equivalent) 
Write-Host "`n=== GuiDisconnected.java (first 60 lines) ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiDisconnected.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
for ($i = 0; $i -lt [Math]::Min(60, $lines.Count); $i++) {
    Write-Host "L$($i+1): $($lines[$i])"
}

# Read GuiTextField.java - key methods
Write-Host "`n=== GuiTextField.java ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiTextField.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
for ($i = 0; $i -lt [Math]::Min(120, $lines.Count); $i++) {
    $l = $lines[$i]
    if ($l -match 'class|extends|implements|public|protected|constructor|void set|boolean is|String get|void render|void draw|setEnabled|setVisible|setEnableBackgroundDrawing|setCanLoseFocus|setText|getText|setFocused|isFocused|setResponder|func_|mouseClicked|keyTyped|charTyped') {
        Write-Host "L$($i+1): $l"
    }
}

# Read GuiListExtended.java - key methods
Write-Host "`n=== GuiListExtended.java ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiListExtended.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
Write-Host ($lines -join "`n")

# Read GuiEventHandler.java
Write-Host "`n=== GuiEventHandler.java ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiEventHandler.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
Write-Host ($lines -join "`n")

# Read GuiScreen.java - key lines
Write-Host "`n=== GuiScreen.java - key methods ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiScreen.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n")
$reader.Close(); $stream.Close()
for ($i = 0; $i -lt $lines.Count; $i++) {
    $l = $lines[$i]
    if ($l -match 'class GuiScreen|extends|implements|public.*void|protected.*void|public.*boolean|protected.*boolean|addButton|buttons|children|field_146292_n|drawCenteredString|drawString|renderBackground|fill\(|blit|func_|font|width|height|close') {
        Write-Host "L$($i+1): $l"
    }
}

$z.Dispose()
