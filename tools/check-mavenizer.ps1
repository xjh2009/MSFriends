# Check what's in the local maven repo output
$base = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\.gradle"
Write-Host "=== .gradle dir structure ==="
Get-ChildItem $base -Recurse -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    $count = (Get-ChildItem $_.FullName -File -ErrorAction SilentlyContinue).Count
    Write-Host "$($_.FullName) ($count files)"
} | Select-Object -First 50

Write-Host "`n=== mavenizer repo ==="
$repo = "$base\mavenizer\repo"
if (Test-Path $repo) {
    Get-ChildItem $repo -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "$($_.FullName) ($($_.Length) bytes)"
    }
} else {
    Write-Host "NOT FOUND"
}
