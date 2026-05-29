Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = Join-Path $env:USERPROFILE ".gradle\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\stable\22"
Write-Host "=== Files in $base ==="
Get-ChildItem $base -Recurse -File | ForEach-Object { Write-Host "$($_.Length)  $($_.FullName)" }

Write-Host ""
Write-Host "=== Checking all JARs for FML ==="
Get-ChildItem $base -Recurse -Filter "*.jar" | ForEach-Object {
    $jar = $_.FullName
    $z = [System.IO.Compression.ZipFile]::OpenRead($jar)
    $hasMod = $z.GetEntry('cpw/mods/fml/common/Mod.class')
    $hasLoading = $z.GetEntry('cpw/mods/fml/relauncher/IFMLLoadingPlugin.class')
    if ($hasMod -or $hasLoading) {
        Write-Host "FOUND FML in: $jar"
        Write-Host "  Mod.class: $([bool]$hasMod)"
        Write-Host "  IFMLLoadingPlugin.class: $([bool]$hasLoading)"
        # List FML entries
        $z.Entries | Where-Object { $_.FullName -like 'cpw/mods/fml/*' -and $_.FullName -like '*.class' } | Select-Object -First 20 | ForEach-Object { Write-Host "  $($_.FullName)" }
    }
    $z.Dispose()
}
