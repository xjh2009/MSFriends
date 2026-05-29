$ErrorActionPreference = "Stop"
$f = Join-Path $env:USERPROFILE ".gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.19.2-20220805.130853\client\data\mappings\joined.tsrg"
$lines = [System.IO.File]::ReadAllLines($f)
$result = @()
for ($i = 0; $i -lt 25; $i++) {
    $line = $lines[$i]
    $tc = 0
    foreach ($c in $line.ToCharArray()) {
        if ($c -eq [char]9) { $tc++ } else { break }
    }
    $result += "T=$tc : $($line.Substring(0, [Math]::Min(80, $line.Length)))"
}
$result += ""
$result += "=== Count by indentation ==="
$t0 = 0; $t1 = 0; $t2 = 0
foreach ($line in $lines) {
    if ($line.StartsWith("tsrg2")) { continue }
    $tc = 0
    foreach ($c in $line.ToCharArray()) {
        if ($c -eq [char]9) { $tc++ } else { break }
    }
    switch ($tc) {
        0 { $t0++ }
        1 { $t1++ }
        2 { $t2++ }
    }
}
$result += "T=0 (class lines): $t0"
$result += "T=1 (member lines): $t1"
$result += "T=2 (param lines): $t2"

# Show a method line with desc to verify
$result += ""
$result += "=== Sample method lines (T=1, starts with desc) ==="
$methodCount = 0
foreach ($line in $lines) {
    $tc = 0
    foreach ($c in $line.ToCharArray()) {
        if ($c -eq [char]9) { $tc++ } else { break }
    }
    if ($tc -eq 1) {
        $trimmed = $line.TrimStart([char]9)
        $parts = $trimmed.Split("`t")
        if ($parts.Count -ge 3 -and $parts[1].StartsWith("(")) {
            $result += "  parts: [$($parts -join '] [')]"
            $methodCount++
            if ($methodCount -ge 5) { break }
        }
    }
}

$result += ""
$result += "=== Sample field lines (T=1, no desc) ==="
$fieldCount = 0
foreach ($line in $lines) {
    $tc = 0
    foreach ($c in $line.ToCharArray()) {
        if ($c -eq [char]9) { $tc++ } else { break }
    }
    if ($tc -eq 1) {
        $trimmed = $line.TrimStart([char]9)
        $parts = $trimmed.Split("`t")
        if ($parts.Count -ge 2 -and -not $parts[1].StartsWith("(")) {
            $result += "  parts: [$($parts -join '] [')]"
            $fieldCount++
            if ($fieldCount -ge 5) { break }
        }
    }
}

$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\tsrg-analysis.txt" -Encoding utf8
Write-Host "Done"