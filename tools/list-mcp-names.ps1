Add-Type -AssemblyName System.IO.Compression.FileSystem
$srcJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($srcJar)
$entries = $z.Entries | Where-Object { $_.FullName -match "Screen|Toast|Button|AbstractList|ResourceLocation|ITextComponent|FontRenderer" -and $_.FullName -match '\.java$' } | Select-Object FullName, Length | Sort-Object FullName
$entries | Select-Object -First 80 | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }
$z.Dispose()
