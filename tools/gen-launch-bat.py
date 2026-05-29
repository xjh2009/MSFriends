import os
cp_file = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\classpath.txt'
with open(cp_file) as f:
    cp_lines = [l.strip() for l in f if l.strip()]
cp = ';'.join(cp_lines)
game_dir = os.path.expandvars(r'%APPDATA%\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4')
assets_dir = os.path.expandvars(r'%APPDATA%\.minecraft\assets')

bat = f"""@echo off
"C:\\Program Files\\Zulu\\zulu-8\\bin\\java.exe" -Xmx2048M -cp "{cp}" net.minecraft.launchwrapper.Launch --username DevPlayer --version "1.9.4-forge1.9.4-12.17.0.2317-1.9.4" --gameDir "{game_dir}" --assetsDir "{assets_dir}" --assetIndex "1.9" --accessToken "0" --uuid "00000000-0000-0000-0000-000000000000" --userType "legacy" --tweakClass "net.minecraftforge.fml.common.launcher.FMLTweaker" --versionType "Forge"
"""

out = r'c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\launch-194.bat'
with open(out, 'w', encoding='ascii') as f:
    f.write(bat)
print(f'Written {len(bat)} chars to launch-194.bat')
print(f'CP entries: {len(cp_lines)}')
