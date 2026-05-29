param([string]$dir)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zips = Get-ChildItem $dir -Recurse -Filter "*.zip" -ErrorAction SilentlyContinue
foreach ($z in $zips) {
    try {
        $arc = [System.IO.Compression.ZipFile]::OpenRead($z.FullName)
        $arc.Dispose()
    } catch {
        Write-Host "CORRUPT: $($z.Length) $($z.FullName)"
    }
}
Write-Host "Total zips: $($zips.Count)"
