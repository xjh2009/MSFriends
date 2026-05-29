Add-Type -AssemblyName System.IO.Compression.FileSystem

# Check ALL jar files in the entire forge cache for 1.13.2 and related directories
# The error is in injectSources which merges patched.jar with forge-sources.jar

# 1. Check forge sources jar from Maven
$sourcesJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\forge\net\minecraftforge\forge\1.13.2-25.0.223\forge-1.13.2-25.0.223-sources.jar"
Write-Host "=== Forge sources jar ==="
if (Test-Path $sourcesJar) {
    $info = Get-Item $sourcesJar
    Write-Host "Size: $($info.Length)"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($sourcesJar)
        Write-Host "Entries: $($z.Entries.Count)"
        $z.Dispose()
        Write-Host "VALID"
    } catch {
        Write-Host "CORRUPTED: $($_.Exception.Message)"
    }
} else {
    Write-Host "NOT FOUND"
}

# 2. Check injected-sources.jar 
$injectedJar = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge\1.13.2-25.0.223\injected-sources.jar"
Write-Host "`n=== Injected-sources jar ==="
if (Test-Path $injectedJar) {
    $info = Get-Item $injectedJar
    Write-Host "Size: $($info.Length)"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($injectedJar)
        Write-Host "Entries: $($z.Entries.Count)"
        $z.Dispose()
        Write-Host "VALID"
    } catch {
        Write-Host "CORRUPTED: $($_.Exception.Message)"
    }
} else {
    Write-Host "NOT FOUND"
}

# 3. Check ALL recent jar files in the entire forge cache tree
Write-Host "`n=== Recently modified jars in forge cache ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -gt (Get-Date).AddMinutes(-5) } | ForEach-Object {
    $valid = "VALID"
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        $z.Dispose()
    } catch {
        $valid = "CORRUPTED"
    }
    Write-Host "$valid $($_.Length) $($_.LastWriteTime) $($_.FullName)"
}

# 4. Also check the injectSources output — it could be using a different path
# The mcmaven log should tell us which files it's merging
$logDir = "$env:USERPROFILE\.gradle\caches\minecraftforge\forgegradle\mavenizer\logs"
Write-Host "`n=== Looking for mcmaven log files ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\minecraftforge" -Recurse -Filter "*.log" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "1.13.2" -and $_.LastWriteTime -gt (Get-Date).AddMinutes(-5) } | ForEach-Object {
    Write-Host "$($_.Length) $($_.FullName)"
    # Read last 20 lines
    Get-Content $_.FullName -Tail 20 | ForEach-Object { Write-Host "  $_" }
}
