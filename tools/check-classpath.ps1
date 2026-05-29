Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check recompiled.jar to see if it uses SRG or MCP names
$recompiledJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13\recompiled.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($recompiledJar)

# Check entries - do they have SRG or MCP names?
Write-Host "=== Sample entries from recompiled.jar ==="
$z.Entries | Where-Object { $_.FullName -match "GuiScreen|Screen|Minecraft" -and $_.FullName -match '\.class$' } | Select-Object -First 20 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }

Write-Host "`n=== Client GUI classes ==="
$z.Entries | Where-Object { $_.FullName -match "^net/minecraft/client/gui/" -and $_.FullName -match '\.class$' } | Select-Object -First 30 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }

$z.Dispose()

# Check remapped-javadoc.jar
$remappedJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13\remapped-javadoc.jar"
$z2 = [System.IO.Compression.ZipFile]::OpenRead($remappedJar)

Write-Host "`n=== Sample entries from remapped-javadoc.jar ==="
$z2.Entries | Where-Object { $_.FullName -match "GuiScreen|Screen|Minecraft" -and $_.FullName -match '\.class$' } | Select-Object -First 20 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }

Write-Host "`n=== Client GUI classes from remapped ==="
$z2.Entries | Where-Object { $_.FullName -match "^net/minecraft/client/gui/" -and $_.FullName -match '\.class$' } | Select-Object -First 30 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }

$z2.Dispose()

# Check the injected.jar (the one used in injectSources)
$injectedJar = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot\20180921-1.13\injected.jar"
if (Test-Path $injectedJar) {
    $z3 = [System.IO.Compression.ZipFile]::OpenRead($injectedJar)
    Write-Host "`n=== Sample entries from injected.jar ==="
    $z3.Entries | Where-Object { $_.FullName -match "GuiScreen|Screen|Minecraft" -and $_.FullName -match '\.class$' } | Select-Object -First 20 -ExpandProperty FullName | ForEach-Object { Write-Host $_ }
    $z3.Dispose()
}
