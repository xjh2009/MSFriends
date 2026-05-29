$ErrorActionPreference = "Stop"
$result = @()
$base = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config"
Get-ChildItem $base -Directory | ForEach-Object {
    $srg = Get-ChildItem $_.FullName -Recurse -Filter "joined.tsrg" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($srg) {
        $result += "$($_.Name): $($srg.FullName) ($($srg.Length) bytes)"
    } else {
        $result += "$($_.Name): no joined.tsrg"
    }
}
# Also check the 1.19.2 specifically
$v1192 = "$base\1.19.2-20220805.130853\client\data\mappings"
$result += "1.19.2 mappings dir exists: $(Test-Path $v1192)"
if (Test-Path $v1192) {
    Get-ChildItem $v1192 | ForEach-Object { $result += "  $($_.Name) ($($_.Length) bytes)" }
}
$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\srg-check2.txt" -Encoding utf8
Write-Host "Done"