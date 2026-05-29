Add-Type -AssemblyName System.IO.Compression.FileSystem

$forgeDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223"

# Check in injected-sources.jar
$srcJar = "$forgeDir\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)

# List all GUI-related entries
$entries = $z.Entries | Where-Object { $_.FullName -match 'gui|widget|screen|button|toast|list|text.?field|confirm|disconnect' -and $_.FullName -like '*.java' } | Select-Object FullName
$entries | Format-Table -AutoSize

# Check for specific classes we need
Write-Host "`n=== Looking for ConfirmScreen ==="
$z.Entries | Where-Object { $_.FullName -match 'Confirm' -and $_.FullName -like '*.java' } | ForEach-Object { Write-Host $_.FullName }

Write-Host "`n=== Looking for DisconnectedScreen ==="
$z.Entries | Where-Object { $_.FullName -match 'Disconnect' -and $_.FullName -like '*.java' } | ForEach-Object { Write-Host $_.FullName }

Write-Host "`n=== Looking for TextFieldWidget ==="
$z.Entries | Where-Object { $_.FullName -match 'TextField' -and $_.FullName -like '*.java' } | ForEach-Object { Write-Host $_.FullName }

Write-Host "`n=== Looking for Slider ==="
$z.Entries | Where-Object { $_.FullName -match 'Slider|Option' -and $_.FullName -like '*.java' } | ForEach-Object { Write-Host $_.FullName }

# Read GuiButton.java
Write-Host "`n=== GuiButton.java ==="
$entry = $z.Entries | Where-Object { $_.FullName -eq "net/minecraft/client/gui/GuiButton.java" }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$content = $reader.ReadToEnd()
$reader.Close()
$stream.Close()
Write-Host $content

# Read GuiTextField.java if it exists
Write-Host "`n=== GuiTextField.java ==="
$tfe = $z.Entries | Where-Object { $_.FullName -match 'TextField' -and $_.FullName -like '*.java' }
if ($tfe) {
    $stream = $tfe[0].Open()
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
    $lines = $content -split "`n"
    for ($i = 0; $i -lt [Math]::Min(80, $lines.Count); $i++) {
        $l = $lines[$i].Trim()
        if ($l -match 'class|extends|implements|public|protected|constructor|GuiTextField|setText|getText|setFocused|isFocused|setResponder|setEnableBackground|setCanLose|setMaxLength|render') {
            Write-Host "L$($i+1): $l"
        }
    }
}

$z.Dispose()
