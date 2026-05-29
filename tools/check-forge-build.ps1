$path = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.20.1\forge\build.gradle.kts"
$content = Get-Content $path -Raw -ErrorAction SilentlyContinue
if ($content) {
    Write-Output "Current content (${$content.Length} chars):"
    Write-Output $content.Substring(0, [Math]::Min(200, $content.Length))
}
