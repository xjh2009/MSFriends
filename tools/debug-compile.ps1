# Get the actual compile classpath from the task
$base = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"
cd $base

# Try printing classpath
$output = .\gradlew :versions:1.13.2:forge:printClasspath 2>&1
Write-Host "printClasspath output:"
$output | Select-Object -First 30 | ForEach-Object { Write-Host $_ }

# Check if there's a mcp jar on the classpath
Write-Host "`n=== Check for mcp-related files ==="
$output2 = .\gradlew :versions:1.13.2:forge:compileJava --info 2>&1 | Out-File build\1132-compile-info.txt -Encoding utf8
$compileOutput = Get-Content build\1132-compile-info.txt
$classpathLines = $compileOutput | Select-String "classpath|Classpath|class path|Compiling|Using" | Select-Object -First 40
$classpathLines | ForEach-Object { Write-Host $_.Line }
