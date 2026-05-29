$ErrorActionPreference = "Stop"
$base = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config"
$result = @()
$result += "Path exists: $(Test-Path $base)"
if (Test-Path $base) {
    Get-ChildItem $base -Directory | ForEach-Object { $result += "Dir: $($_.Name)" }
    $f = Get-ChildItem $base -Recurse -Filter "joined.tsrg" | Select-Object -First 1
    if ($f) {
        $result += "Found: $($f.FullName)"
        $result += "Size: $($f.Length)"
        Get-Content $f.FullName -TotalCount 8 | ForEach-Object { $result += "  $_" }
    } else {
        $result += "No joined.tsrg found"
    }
} else {
    $result += "Searching broader..."
    $alt = "$env:USERPROFILE\.gradle\caches\minecraftforge"
    if (Test-Path $alt) {
        Get-ChildItem $alt -Recurse -Filter "joined.tsrg" -ErrorAction SilentlyContinue | Select-Object -First 3 | ForEach-Object { $result += "Found: $($_.FullName) ($($_.Length) bytes)" }
    }
    $alt2 = "$env:USERPROFILE\.gradle\caches"
    if (Test-Path $alt2) {
        Get-ChildItem $alt2 -Recurse -Filter "joined.tsrg" -ErrorAction SilentlyContinue | Select-Object -First 3 | ForEach-Object { $result += "Found: $($_.FullName) ($($_.Length) bytes)" }
    }
}
$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\srg-check.txt" -Encoding utf8
Write-Host "Done. Results in tools/srg-check.txt"