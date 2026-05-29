Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check recompiled.jar class names
$recompiledJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13\recompiled.jar"
Write-Host "=== recompiled.jar ==="
$z = [System.IO.Compression.ZipFile]::OpenRead($recompiledJar)
Write-Host "Size: $($z.Entries.Count) entries"

# Check GUI classes
$z.Entries | Where-Object { $_.FullName -match "^net/minecraft/client/gui/" -and $_.FullName -match '\.class$' -and $_.FullName -notmatch '\$' } | Select-Object -First 30 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }
$z.Dispose()

# Check the snapshot dir for the final output
Write-Host "`n=== All jars in snapshot dir ==="
$snapshotDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13"
Get-ChildItem $snapshotDir -Filter "*.jar" | ForEach-Object {
    $z2 = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
    Write-Host "$($_.Name): $($z2.Entries.Count) entries"
    
    # Check for GuiScreen
    $guiScreenEntries = $z2.Entries | Where-Object { $_.FullName -match "GuiScreen\.class$" }
    Write-Host "  GuiScreen entries: $($guiScreenEntries.Count)"
    $guiScreenEntries | ForEach-Object { Write-Host "    $($_.FullName)" }
    
    $screenEntries = $z2.Entries | Where-Object { $_.FullName -match "Screen\.class$" -and $_.FullName -notmatch "GuiScreen" }
    Write-Host "  Screen (non-Gui) entries: $($screenEntries.Count)"
    $screenEntries | ForEach-Object { Write-Host "    $($_.FullName)" }
    
    $z2.Dispose()
}

# Now check the injected.jar specifically - this is likely the compile artifact
$injectedJar = "$snapshotDir\injected.jar"
if (Test-Path $injectedJar) {
    $z3 = [System.IO.Compression.ZipFile]::OpenRead($injectedJar)
    Write-Host "`n=== injected.jar GUI classes ==="
    $z3.Entries | Where-Object { $_.FullName -match "^net/minecraft/client/gui/" -and $_.FullName -match '\.class$' -and $_.FullName -notmatch '\$' } | Select-Object -First 30 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }
    
    # Check for the actual class that the jar provides
    Write-Host "`n=== All top-level packages ==="
    $z3.Entries | Where-Object { $_.FullName -match '^net/' -and $_.FullName -match '\.class$' -and $_.FullName -notmatch '\$' -and ($_.FullName -split '/').Count -le 4 } | Select-Object -ExpandProperty FullName | Sort-Object -Unique | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
    $z3.Dispose()
}
