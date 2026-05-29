@echo off
set JAVA_HOME=C:\Program Files\Zulu\zulu-21
cd /d "%~dp0.."
echo === Stopping Gradle daemon ===
call gradlew.bat --stop 2>&1
echo === Building 1.14.4 forge ===
call gradlew.bat :versions:1.14.4:forge:compileJava --info --no-daemon 2>&1
echo === Exit code: %ERRORLEVEL% ===
