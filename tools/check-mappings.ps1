$ErrorActionPreference = "Stop"

# Step 1: Extract forge.srg
$forgeSrgDir = "$env:TEMP\forge-srg"
if (Test-Path $forgeSrgDir) { Remove-Item $forgeSrgDir -Recurse -Force }
New-Item -ItemType Directory $forgeSrgDir -Force | Out-Null
Push-Location $forgeSrgDir
& jar xf "$env:APPDATA\.minecraft\libraries\net\minecraftforge\forge\1.19.2-43.4.0\forge-1.19.2-43.4.0-universal.jar" forge.srg
Pop-Location
Write-Host "=== forge.srg (first 30 lines) ==="
Get-Content "$forgeSrgDir\forge.srg" -TotalCount 30

# Step 2: Extract joined.tsrg
$joinedTsrg = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.19.2-20220805.130853\client\data\mappings\joined.tsrg"
Write-Host "`n=== joined.tsrg (first 30 lines) ==="
Get-Content $joinedTsrg -TotalCount 30

# Step 3: Check Fabric Loom mappings
$loomDir = "$env:USERPROFILE\.gradle\caches\fabric-loom\1.19.2"
Write-Host "`n=== Fabric Loom 1.19.2 cache ==="
Get-ChildItem $loomDir -Recurse -File -ErrorAction SilentlyContinue | Select-Object Name,Length | Format-Table -AutoSize

# Step 4: Find mappings-base.tiny
$yarnMapping = Get-ChildItem $loomDir -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -eq "mappings-base.tiny" } | Select-Object -First 1
if ($yarnMapping) {
    Write-Host "`n=== mappings-base.tiny (first 30 lines) ==="
    Get-Content $yarnMapping.FullName -TotalCount 30
}
