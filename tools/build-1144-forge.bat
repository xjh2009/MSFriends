@echo off
set JAVA_HOME=C:\Program Files\Zulu\zulu-21
cd /d "%~dp0"
call gradlew.bat :versions:1.14.4:forge:compileJava 2>&1
