Add-Type -AssemblyName System.IO.Compression.FileSystem
$srcJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)

# Get ALL method/field signatures from Gui.java
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/Gui.java" }
$stream = $entry.Open(); $reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n"); $reader.Close(); $stream.Close()
Write-Host "=== Gui.java FULL SOURCE ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    $l = $lines[$i].Trim()
    if ($l -match 'public|protected|private|static.*void|static.*int|blit|draw|func_|field_') {
        Write-Host "L$($i+1): $l"
    }
}

# Get ALL GuiTextField methods  
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiTextField.java" }
$stream = $entry.Open(); $reader = New-Object System.IO.StreamReader($stream)
$lines = ($reader.ReadToEnd() -split "`n"); $reader.Close(); $stream.Close()
Write-Host "`n=== GuiTextField.java FULL METHODS ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    $l = $lines[$i].Trim()
    if ($l -match 'public|protected|func_|field_' -and $l -notmatch '^\*' -and $l -notmatch '^\*' -and $l -notmatch '//') {
        Write-Host "L$($i+1): $l"
    }
}

# Get Minecraft key methods
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/Minecraft.java" }
$stream = $entry.Open(); $reader = New-Object System.IO.StreamReader($stream)
$content = $reader.ReadToEnd(); $reader.Close(); $stream.Close()
$lines = $content -split "`n"
Write-Host "`n=== Minecraft.java KEY METHODS ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    $l = $lines[$i].Trim()
    if ($l -match 'displayGuiScreen|func_147108_a|func_71410_x|getSkinManager|func_152343_a|func_110434_K|getTextureManager|field_71466_p|fontRenderer|func_175599_af|getSessionService|execute|getToastGui|addScheduledTask|func_71369_N') {
        Write-Host "L$($i+1): $l"
    }
}

# Get DefaultPlayerSkin methods
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/resources/DefaultPlayerSkin.java" }
if ($entry) {
    $stream = $entry.Open(); $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd(); $reader.Close(); $stream.Close()
    Write-Host "`n=== DefaultPlayerSkin.java ==="
    Write-Host $content
}

$z.Dispose()
