Add-Type -AssemblyName System.IO.Compression.FileSystem

# The forge userdev jar contains config.json with tool definitions
$forgeDir = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge"
Write-Host "=== Forge userdev jar ==="
Get-ChildItem $forgeDir -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }

# Check all jars in the entire forgegradle cache
Write-Host "`n=== All JARs in forgegradle ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }

# Check jars in modules-2 cache (Maven dependencies)
Write-Host "`n=== Checking SpecialSource and MCP tools in modules-2 ==="
$modules = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
Get-ChildItem $modules -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "specialsource|fernflower|mcinject|access-widener|mergetool" } | ForEach-Object { Write-Host "$($_.Length) $($_.FullName)" }
