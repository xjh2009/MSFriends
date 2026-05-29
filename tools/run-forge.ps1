Start-Process -FilePath "C:\Users\xjh37\zulu-11\bin\java.exe" -ArgumentList @(
    "-Xmx2048M",
    "-Dmsf.cpfile=c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\classpath.txt",
    "-cp", "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools",
    "Java17Bootstrap",
    "--username", "DevPlayer",
    "--version", "1.9.4-forge1.9.4-12.17.0.2317-1.9.4",
    "--gameDir", "$env:APPDATA\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4",
    "--assetsDir", "$env:APPDATA\.minecraft\assets",
    "--assetIndex", "1.9",
    "--accessToken", "0",
    "--uuid", "00000000-0000-0000-0000-000000000000",
    "--userType", "legacy",
    "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
    "--versionType", "Forge"
) -PassThru | Select-Object Id
