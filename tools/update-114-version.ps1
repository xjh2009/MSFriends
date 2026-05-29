$javaDir = ".\versions\1.14\forge\src\main\java"
$files = Get-ChildItem $javaDir -Recurse -Filter "*.java"
Write-Output "Found $($files.Count) Java files"
foreach ($f in $files) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $content = $content.Replace('MC 1.13.2', 'MC 1.14.4')
    $content = $content.Replace('Forge 1.13.2', 'Forge 1.14.4')
    $content = $content.Replace('1.13.2 MCP', '1.14.4 MCP')
    $content = $content.Replace('on 1.13.2', 'on 1.14.4')
    $content = $content.Replace('for 1.13.2', 'for 1.14.4')
    $content = $content.Replace('1.13.2 version', '1.14.4 version')
    $content = $content.Replace('MSF-forge-1.13.2', 'MSF-forge-1.14.4')
    [System.IO.File]::WriteAllText($f.FullName, $content, [System.Text.UTF8Encoding]::new($false))
}
Write-Output "Done"
