Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead('C:\Users\xjh37\Desktop\MSF\msf-friends-multi\build\common\libs\common-0.1.0+26.1.2.jar')
$logging = $z.Entries | Where-Object { $_.FullName -like '*Logging*' }
foreach ($e in $logging) {
    Write-Host "Name: $($e.FullName)  Size: $($e.Length)"
    # Read bytecode magic number
    $stream = $e.Open()
    $buf = New-Object byte[] 4
    $stream.Read($buf, 0, 4) | Out-Null
    Write-Host "Magic: $([BitConverter]::ToString($buf))"
    # Read class version
    $buf2 = New-Object byte[] 2
    $stream.Read($buf2, 0, 2) | Out-Null
    $minor = [BitConverter]::ToUInt16($buf2, 0)
    $stream.Read($buf2, 0, 2) | Out-Null
    $major = [BitConverter]::ToUInt16($buf2, 0)
    Write-Host "Version: $major.$minor (Java $([int]($major - 44)))"
    $stream.Close()
}
$z.Dispose()
