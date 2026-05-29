# Check MCP mapping CSVs to see which names are human-mapped vs SRG
$gradleCache = "$env:USERPROFILE\.gradle\caches"

# Find the MCP config directory
$mcpConfigDir = Get-ChildItem -Path $gradleCache -Recurse -Directory -Filter "1.13.2*" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match 'mcp_config|mcp\/snapshot' } | Select-Object -First 10
Write-Host "MCP config dirs:"
$mcpConfigDir | ForEach-Object { Write-Host "  $($_.FullName)" }

# Also look for methods.csv or fields.csv
$csvFiles = Get-ChildItem -Path "$gradleCache\forgegradle" -Recurse -Filter "*.csv" -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'method|field|param' -and $_.FullName -match '1\.13|2018' } | Select-Object -First 20
Write-Host "`nCSV files:"
$csvFiles | ForEach-Object { Write-Host "  $($_.FullName) ($($_.Length) bytes)" }

# Also check for tsrg or tiny files
$mappings = Get-ChildItem -Path "$gradleCache\forgegradle" -Recurse -Filter "*.tsrg" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '1\.13|2018' } | Select-Object -First 10
Write-Host "`nTSRG files:"
$mappings | ForEach-Object { Write-Host "  $($_.FullName) ($($_.Length) bytes)" }

# Check for snapshot files
$snapshotDir = Get-ChildItem -Path "$gradleCache\forgegradle" -Recurse -Directory -Filter "20180921*" -ErrorAction SilentlyContinue | Select-Object -First 5
Write-Host "`nSnapshot dirs:"
$snapshotDir | ForEach-Object { Write-Host "  $($_.FullName)" }
