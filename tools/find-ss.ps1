# Search for SpecialSource anywhere in Gradle cache
Write-Host "=== Searching for SpecialSource in all caches ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "*SpecialSource*" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.Length) $($_.FullName)" 
}

Write-Host "`n=== Searching for SpecialSource in modules-2 ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2" -Recurse -Filter "*SpecialSource*" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.Length) $($_.FullName)" 
}

Write-Host "`n=== Checking mcinjector ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "*mcinjector*" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.Length) $($_.FullName)" 
}

Write-Host "`n=== Checking forgeflower ==="
Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "*forgeflower*" -ErrorAction SilentlyContinue | ForEach-Object { 
    Write-Host "$($_.Length) $($_.FullName)" 
}
