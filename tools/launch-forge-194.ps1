$cpFile = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\classpath.txt"
$bootstrap = "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools"
$gameDir = "$env:APPDATA\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4"
$mcDir = "$env:APPDATA\.minecraft"
$java = "C:\Program Files\Zulu\zulu-17\bin\java.exe"

& $java -Xmx2048M `
    "-Dmsf.cpfile=$cpFile" `
    -cp "$bootstrap" `
    Java17Bootstrap `
    --username DevPlayer `
    --version 1.9.4-forge1.9.4-12.17.0.2317-1.9.4 `
    --gameDir "$gameDir" `
    --assetsDir "$mcDir\assets" `
    --assetIndex 1.9 `
    --accessToken 0 `
    --uuid 00000000-0000-0000-0000-000000000000 `
    --userType legacy `
    --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker `
    --versionType Forge
