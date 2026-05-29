$ErrorActionPreference = "Stop"
$result = @()

$srgFile = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.19.2-20220805.130853\client\data\mappings\joined.tsrg"
$tinyFile = "$env:USERPROFILE\.gradle\caches\fabric-loom\1.19.2\net.fabricmc.yarn.1_19_2.1.19.2+build.28\mappings-base.tiny"

# Check tiny file paths
$loomDir = "$env:USERPROFILE\.gradle\caches\fabric-loom\1.19.2"
Get-ChildItem $loomDir -Directory | Where-Object { $_.Name -like "*yarn*" } | ForEach-Object { $result += "Loom dir: $($_.Name)" }
$baseTinys = Get-ChildItem $loomDir -Recurse -Filter "mappings-base.tiny" -ErrorAction SilentlyContinue
$baseTinys | ForEach-Object { $result += "Tiny: $($_.FullName) ($($_.Length) bytes)" }

$result += ""
$result += "=== joined.tsrg first 20 lines ==="
Get-Content $srgFile -TotalCount 20 | ForEach-Object { $result += $_ }

$result += ""
$result += "=== Count SRG classes ==="
$srgClassCount = 0
Get-Content $srgFile | ForEach-Object {
    if (-not $_.StartsWith("tsrg2") -and -not $_.StartsWith("`t") -and $_.Trim().Length -gt 0) {
        $srgClassCount++
    }
}
$result += "Total SRG class lines: $srgClassCount"

$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\srg-check3.txt" -Encoding utf8
Write-Host "Done"