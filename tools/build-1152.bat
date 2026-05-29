@echo off
cd /d c:\Users\xjh37\Desktop\MSF\msf-friends-multi

echo Writing settings.gradle.kts...
powershell -Command "[System.IO.File]::WriteAllText('settings.gradle.kts', (Get-Content -Raw 'tools\build-1152-settings.txt'), [System.Text.UTF8Encoding]::new($false))"
echo Starting build...
call gradlew.bat :versions:1.15.2:forge:compileJava --no-daemon > tools\build-1152-output.txt 2>&1
echo Build finished with exit code %ERRORLEVEL%
type tools\build-1152-output.txt | powershell -Command "$input | Select-Object -Last 80"
