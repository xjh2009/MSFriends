@echo off
setlocal
set JAVA_HOME=C:\Program Files\Zulu\zulu-21
cd /d "c:\Users\xjh37\Desktop\MSF\msf-friends-multi"

echo === Killing competing Java processes ===
taskkill /F /IM java.exe 2>nul
timeout /t 2 /nobreak >nul

echo === Writing settings.gradle.kts ===
node tools\_write_settings.js

echo === Building Forge 1.17.1 jar ===
call gradlew.bat :versions:1.17.1:forge:jar --no-daemon 2>&1

echo === Build finished with exit code %ERRORLEVEL% ===
exit /b %ERRORLEVEL%
