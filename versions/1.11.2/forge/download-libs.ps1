$ErrorActionPreference = "Continue"
$mcDir = "C:\Users\xjh37\AppData\Roaming\.minecraft"
$jsonPath = Join-Path $mcDir "versions\1.11.2-forge-13.20.1.2588\1.11.2-forge-13.20.1.2588.json"
$raw = [System.IO.File]::ReadAllText($jsonPath)
$ver = ConvertFrom-Json $raw
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$count = 0
foreach ($lib in $ver.libraries) {
    $parts = $lib.name -split ':'
    $grp = $parts[0] -replace '\.','/'
    $art = $parts[1]
    $ver2 = $parts[2]
    $jarPath = Join-Path $mcDir "libraries\$grp\$art\$ver2\$art-$ver2.jar"
    if (-not (Test-Path $jarPath)) {
        $count++
        $baseUrl = if ($lib.url) { $lib.url } else { "https://libraries.minecraft.net/" }
        $dlUrl = "${baseUrl}${grp}/${art}/${ver2}/${art}-${ver2}.jar"
        $dir3 = Split-Path $jarPath -Parent
        New-Item -ItemType Directory -Path $dir3 -Force | Out-Null
        try {
            Invoke-WebRequest -Uri $dlUrl -OutFile $jarPath -UseBasicParsing -ErrorAction Stop
            Write-Host "OK: $($lib.name)"
        } catch {
            Write-Host "FAIL: $($lib.name) => $dlUrl => $($_.Exception.Message)"
        }
    }
}
Write-Host "Total downloaded: $count"
