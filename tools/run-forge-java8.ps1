$cpLines = Get-Content "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\tools\classpath.txt"
$cp = $cpLines -join ";"
$java8 = "C:\Program Files\Zulu\zulu-8\bin\java.exe"
$gameDir = "$env:APPDATA\.minecraft\versions\1.9.4-forge1.9.4-12.17.0.2317-1.9.4"
$assetsDir = "$env:APPDATA\.minecraft\assets"
Write-Host "[runner] classpath has $($cpLines.Count) entries"
Write-Host "[runner] launching with Java 8..."
$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = $java8
$pinfo.Arguments = "-Xmx2048M -cp `"$cp`" net.minecraft.launchwrapper.Launch --username DevPlayer --version 1.9.4-forge1.9.4-12.17.0.2317-1.9.4 --gameDir `"$gameDir`" --assetsDir `"$assetsDir`" --assetIndex 1.9 --accessToken 0 --uuid 00000000-0000-0000-0000-000000000000 --userType legacy --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker --versionType Forge"
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError = $true
$pinfo.UseShellExecute = $false
$pinfo.CreateNoWindow = $true
$p = [System.Diagnostics.Process]::Start($pinfo)
$stdout = $p.StandardOutput.ReadToEndAsync()
$stderr = $p.StandardError.ReadToEndAsync()
if (!$p.WaitForExit(45000)) {
    Write-Host "[runner] Process still running after 45s (good sign!)"
    $p.Kill()
} else {
    Write-Host "[runner] Process exited with code $($p.ExitCode)"
}
Write-Host "=== STDOUT ==="
$stdout.Result | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
Write-Host "=== STDERR ==="
$stderr.Result | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
