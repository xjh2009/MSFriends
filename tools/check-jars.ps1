param([string]$dir)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$allJars = Get-ChildItem $dir -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue
foreach ($jar in $allJars) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        $z.Dispose()
    } catch {
        Write-Host "CORRUPTED: $($jar.Length) $($jar.FullName)"
    }
}
Write-Host "Check complete. Total jars: $($allJars.Count)"
