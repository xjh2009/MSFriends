# Check what's in the ForgeGradle build output for 1.13.2
$base = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.13.2\forge"

# Check build directories
Write-Host "=== Build output structure ==="
Get-ChildItem "$base\build" -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.FullName) ($($_.Length) bytes)"
}

Write-Host "`n=== Check for recompiled/remapped in gradle caches ==="
$mcpDir = "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223"
Get-ChildItem $mcpDir -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.FullName) ($($_.Length) bytes)"
}

# Check for remapped jar in the forge 1.13.2 cache
Write-Host "`n=== Check global MCP cache ==="
Get-ChildItem "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\recompiled.jar" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "recompiled: $($_.Length) bytes" }
Get-ChildItem "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\remapped-*.jar" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "remapped: $($_.FullName) ($($_.Length) bytes)" }

# Check for snapshot-specific directory
Write-Host "`n=== Snapshot dir ==="
Get-ChildItem "C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\snapshot" -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.FullName) ($($_.Length) bytes)"
}

# Check the compileJava task classpath - look at the build/classes directory
Write-Host "`n=== Build classes ==="
Get-ChildItem "$base\build\classes" -Recurse -File -Filter "*.class" -ErrorAction SilentlyContinue | Select-Object -First 10 | ForEach-Object { 
    Write-Host "$($_.FullName)"
}

# Check if there's a loom-cache or similar with the remapped jar
Write-Host "`n=== Loom cache ==="
Get-ChildItem "$base\build\loom-cache" -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.FullName) ($($_.Length) bytes)"
}
