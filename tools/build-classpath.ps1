$libDir = "$env:APPDATA\.minecraft\libraries"
$mcDir = "$env:APPDATA\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4"
$forgeJar = "$mcDir\1.9.4-forge1.9.4-12.17.0.2317-1.9.4.jar"
$vanillaJar = "$env:APPDATA\.minecraft\versions\1.9.4\1.9.4.jar"

$allJars = [System.Collections.Generic.List[string]]::new()

function Add-LibrariesFromJson($jsonPath) {
    $json = Get-Content $jsonPath -Raw | ConvertFrom-Json
    foreach ($lib in $json.libraries) {
        $name = [string]$lib.name
        $parts = $name.Split(":")
        if ($parts.Count -ge 3) {
            $groupId = $parts[0] -replace '\.', '/'
            $artifactId = $parts[1]
            $version = $parts[2]
            $classifier = ""
            if ($parts.Count -ge 4) { $classifier = "-$($parts[3])" }
            $jarName = "$artifactId-$version$classifier.jar"
            $fullPath = Join-Path $libDir "$groupId/$artifactId/$version/$jarName"
            if (Test-Path $fullPath) {
                $allJars.Add($fullPath)
            }
        }
    }
}

$vanillaJson = "$env:APPDATA\.minecraft\versions\1.9.4\1.9.4.json"
if (Test-Path $vanillaJson) { Add-LibrariesFromJson $vanillaJson }

$forgeJson = "$mcDir\1.9.4-forge1.9.4-12.17.0.2317-1.9.4.json"
if (Test-Path $forgeJson) { Add-LibrariesFromJson $forgeJson }

if (Test-Path $forgeJar) { $allJars.Add($forgeJar) }
if (Test-Path $vanillaJar) { $allJars.Add($vanillaJar) }

$unique = $allJars | Sort-Object -Unique
$cpFile = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\classpath.txt"
$unique -join "`n" | Set-Content $cpFile -Encoding UTF8

Write-Host "Total classpath entries: $($unique.Count)"
Get-Content $cpFile | Select-Object -First 5
Write-Host "..."
Get-Content $cpFile | Select-Object -Last 5
