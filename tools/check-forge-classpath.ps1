# Find the Forge 1.13.2 artifact jar in the Gradle cache
$gradleCache = "$env:USERPROFILE\.gradle\caches"

# Check for the forge jar
Write-Host "=== Forge 1.13.2 artifact jars ==="
Get-ChildItem -Path $gradleCache -Recurse -File -ErrorAction SilentlyContinue | Where-Object { 
    $_.Name -match "forge.*1\.13\.2.*25\.0\.223" -and $_.Extension -eq ".jar" 
} | Select-Object FullName, Length | Format-Table -AutoSize

# Also check the common Gradle module cache
Write-Host "`n=== Common Gradle module cache for forge 1.13.2 ==="
Get-ChildItem -Path "$gradleCache\modules-2\files-2.1\net.minecraftforge" -Recurse -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "forge" } | ForEach-Object {
    Get-ChildItem $_.FullName -Recurse -File -Filter "*.jar" | ForEach-Object { Write-Host "$($_.FullName) ($($_.Length) bytes)" }
}

# Check the ForgeGradle mavenizer local repo
Write-Host "`n=== ForgeGradle mavenizer local repo ==="
$mavenDir = "$gradleCache\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.13.2-25.0.223"
if (Test-Path $mavenDir) {
    Get-ChildItem $mavenDir -Recurse -File | ForEach-Object { Write-Host "$($_.FullName) ($($_.Length) bytes)" }
} else {
    Write-Host "NOT FOUND: $mavenDir"
}

# Check what the Forge binary jar contains
$forgeJar = Get-ChildItem -Path $mavenDir -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
if ($forgeJar) {
    Write-Host "`n=== Contents of $($forgeJar.Name) ==="
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [System.IO.Compression.ZipFile]::OpenRead($forgeJar.FullName)
    $netMinecraft = $z.Entries | Where-Object { $_.FullName -match '^net/minecraft/' } | Select-Object -First 20
    Write-Host "net/minecraft entries (first 20):"
    $netMinecraft | ForEach-Object { Write-Host "  $($_.FullName) ($($_.Length) bytes)" }
    $totalNetMinecraft = ($z.Entries | Where-Object { $_.FullName -match '^net/minecraft/' }).Count
    Write-Host "Total net/minecraft entries: $totalNetMinecraft"
    
    # Check for specific classes
    $resourceLocation = $z.Entries | Where-Object { $_.FullName -match 'ResourceLocation\.class$' }
    Write-Host "`nResourceLocation entries:"
    $resourceLocation | ForEach-Object { Write-Host "  $($_.FullName) ($($_.Length) bytes)" }
    
    $textComponent = $z.Entries | Where-Object { $_.FullName -match 'ITextComponent\.class$' }
    Write-Host "`nITextComponent entries:"
    $textComponent | ForEach-Object { Write-Host "  $($_.FullName) ($($_.Length) bytes)" }
    
    $z.Dispose()
} else {
    Write-Host "No Forge binary jar found in $mavenDir"
}

# Also check in the standard modules-2 cache
Write-Host "`n=== Standard modules-2 cache ==="
$moduleForge = Get-ChildItem -Path "$gradleCache\modules-2" -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "forge-1\.13\.2.*\.jar" -and $_.Name -notmatch "sources|javadoc" } | Select-Object -First 5
$moduleForge | ForEach-Object { Write-Host "$($_.FullName) ($($_.Length) bytes)" }
