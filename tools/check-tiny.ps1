$ErrorActionPreference = "Stop"
$tinyFile = "$env:USERPROFILE\.gradle\caches\fabric-loom\1.19.2\net.fabricmc.yarn.1_19_2.1.19.2+build.28\mappings-base.tiny"
$result = @()
$result += "=== First 30 lines of mappings-base.tiny ==="
Get-Content $tinyFile -TotalCount 30 | ForEach-Object { $result += $_ }
$result += ""
$result += "=== Header ==="
Get-Content $tinyFile -TotalCount 1 | ForEach-Object { $result += $_ }
$result += ""
$result += "=== Some CLASS lines ==="
$classCount = 0
Get-Content $tinyFile | ForEach-Object {
    if ($_ -match "^CLASS\s") {
        $result += $_
        $classCount++
        if ($classCount -ge 5) { break }
    }
}
$result += ""
$result += "=== Some METHOD lines ==="
$methodCount = 0
Get-Content $tinyFile | ForEach-Object {
    if ($_ -match "^METHOD\s") {
        $result += $_
        $methodCount++
        if ($methodCount -ge 10) { break }
    }
}
$result += ""
$result += "=== Some FIELD lines ==="
$fieldCount = 0
Get-Content $tinyFile | ForEach-Object {
    if ($_ -match "^FIELD\s") {
        $result += $_
        $fieldCount++
        if ($fieldCount -ge 5) { break }
    }
}
$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\tiny-format.txt" -Encoding utf8
Write-Host "Done"