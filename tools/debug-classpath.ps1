# Debug the compile classpath
$base = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"
cd $base

# Get the actual compile classpath
$output = .\gradlew :versions:1.13.2:forge:compileJava --info 2>&1
$classPathLines = $output | Where-Object { $_ -match 'Class path|classpath|compile.*classpath|Compiling' } | Select-Object -First 20
Write-Host "=== Classpath-related lines ==="
$classPathLines | ForEach-Object { Write-Host $_ }

# Also try to get the resolved classpath
Write-Host "`n=== Checking resolved compile classpath ==="
$output2 = .\gradlew :versions:1.13.2:forge:dependencies --configuration compileClasspath 2>&1
$mcLines = $output2 | Where-Object { $_ -match 'forge|mcp|minecraft|recompiled' }
Write-Host "Minecraft-related entries:"
$mcLines | ForEach-Object { Write-Host $_ }
