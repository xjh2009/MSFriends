$ErrorActionPreference = "Stop"
$srgFile = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.19.2-20220805.130853\client\data\mappings\joined.tsrg"
$result = @()

$lines = [System.IO.File]::ReadAllLines($srgFile)
$result += "Total lines: $($lines.Count)"

# Check field vs method parsing
$memberLines = @()
foreach ($line in $lines) {
    if ($line.Length -gt 0 -and $line[0] -eq [char]9) {
        $trimmed = $line.Substring(1)
        $parts = $trimmed.Split("`t")
        $memberLines += [PSCustomObject]@{
            Parts = $parts.Count
            First = $parts[0]
            Second = if ($parts.Count -ge 2) { $parts[1] } else { "" }
            IsMethod = ($parts.Count -ge 2 -and $parts[1].StartsWith("("))
        }
    }
}

$methods = $memberLines | Where-Object { $_.IsMethod }
$fields = $memberLines | Where-Object { -not $_.IsMethod }
$result += "Total member lines: $($memberLines.Count)"
$result += "Method lines: $($methods.Count)"
$result += "Field lines: $($fields.Count)"
$result += "  Methods have $($methods[0].Parts) parts"
$result += "  Fields have $($fields[0].Parts) parts"
$result += ""
$result += "Sample field: $($fields[0].First) -> $($fields[0].Second)"
$result += "Sample method: $($methods[0].First) $($methods[0].Second) -> $($methods[2].Second)"

$result | Out-File "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\tsrg-analysis2.txt" -Encoding utf8
Write-Host "Done"