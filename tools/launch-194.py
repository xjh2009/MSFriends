#!/usr/bin/env python3
"""Launch Forge 1.9.4 with Java 17 via URLClassLoader bootstrap."""
import subprocess, os

ver_dir = r"C:\Users\xjh37\AppData\Roaming\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4"
mc_dir  = r"C:\Users\xjh37\AppData\Roaming\.minecraft"
tools_dir = os.path.dirname(os.path.abspath(__file__))

cp_file = os.path.join(ver_dir, "classpath.txt")
java = r"C:\Program Files\Zulu\zulu-17\bin\java.exe"
natives_dir = os.path.join(ver_dir, "natives")

cmd = [
    java,
    "-Xmx4096M",
    "-XX:+UseG1GC",
    "-Djava.library.path=" + natives_dir,
    "-Dmsf.cpfile=" + cp_file,
    "-cp", tools_dir,
    "Java17Bootstrap",
    "--username", "DevPlayer",
    "--version", "1.9.4",
    "--gameDir", ver_dir,
    "--assetsDir", os.path.join(mc_dir, "assets"),
    "--assetIndex", "1.9",
    "--uuid", "00000000-0000-0000-0000-000000000001",
    "--accessToken", "test",
    "--userType", "mojang",
    "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
    "--versionType", "Forge",
]

print(f"Launching with: {java}")
proc = subprocess.Popen(cmd, cwd=ver_dir)
print(f"PID: {proc.pid}")
proc.wait()
print(f"Exit code: {proc.returncode}")
