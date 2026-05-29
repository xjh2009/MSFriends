@echo off
setlocal

cd /d c:\Users\xjh37\Desktop\MSF\msf-friends-multi

:: Write settings.gradle.kts
(
echo pluginManagement {
echo     repositories {
echo         gradlePluginPortal^(^)
echo         mavenCentral^(^)
echo         maven { url = uri^("https://maven.fabricmc.net/"^) }
echo         maven { url = uri^("https://maven.neoforged.net/releases/"^) }
echo         maven { url = uri^("https://maven.minecraftforge.net/"^) }
echo         maven { url = uri^("https://repo.spongepowered.org/maven/"^) }
echo     }
echo }
echo dependencyResolutionManagement {
echo     repositories {
echo         mavenCentral^(^)
echo         maven { url = uri^("https://maven.fabricmc.net/"^) }
echo         maven { url = uri^("https://libraries.minecraft.net/"^) }
echo         maven { url = uri^("https://maven.minecraftforge.net/"^) }
echo     }
echo }
echo rootProject.name = "MSF"
echo include^(":common"^)
echo include^(":versions:1.21.1:common"^)
echo include^(":versions:1.21.1:fabric"^)
echo include^(":versions:1.21.1:neoforge"^)
echo include^(":versions:1.21.11:common"^)
echo include^(":versions:1.21.11:fabric"^)
echo include^(":versions:1.21.11:neoforge"^)
echo include^(":versions:26.1.2:common"^)
echo include^(":versions:26.1.2:fabric"^)
echo include^(":versions:26.1.2:neoforge"^)
echo include^(":versions:1.20.1:common"^)
echo include^(":versions:1.20.1:fabric"^)
echo include^(":versions:1.20.1:forge"^)
echo include^(":versions:1.19.2:common"^)
echo include^(":versions:1.19.2:fabric"^)
echo include^(":versions:1.19.2:forge"^)
echo include^(":versions:1.18.2:common"^)
echo include^(":versions:1.18.2:fabric"^)
echo include^(":versions:1.18.2:forge"^)
echo include^(":versions:1.17.1:common"^)
echo include^(":versions:1.17.1:fabric"^)
echo include^(":versions:1.17.1:forge"^)
echo include^(":versions:1.16.5:common"^)
echo include^(":versions:1.16.5:fabric"^)
echo include^(":versions:1.15.2:common"^)
echo include^(":versions:1.15.2:fabric"^)
echo include^(":versions:1.15.2:forge"^)
) > settings.gradle.kts

echo Settings written.
echo Building 1.15.2 Forge...
call gradlew.bat :versions:1.15.2:forge:compileJava --no-daemon
echo Exit code: %ERRORLEVEL%
