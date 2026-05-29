# Downgrade Java 17 (61) class files to Java 11 (55) in a jar
# Class file format: bytes 0-3 = magic (CAFEBABE), bytes 4-5 = minor, bytes 6-7 = major (big-endian)
# Java 17 = major 61, Java 11 = major 55

param(
    [string]$InputJar,
    [string]$OutputJar = $InputJar
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$tempDir = "$env:TEMP\classdowngrade-$(Get-Random)"
New-Item -ItemType Directory $tempDir -Force | Out-Null

try {
    # Extract jar
    [System.IO.Compression.ZipFile]::ExtractToDirectory($InputJar, $tempDir)
    
    # Find and patch all .class files
    $patched = 0
    Get-ChildItem $tempDir -Recurse -Filter "*.class" | ForEach-Object {
        $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
        if ($bytes.Length -ge 8) {
            # Check CAFEBABE magic
            if ($bytes[0] -eq 0xCA -and $bytes[1] -eq 0xFE -and $bytes[2] -eq 0xBA -and $bytes[3] -eq 0xBE) {
                $major = [int]$bytes[6] * 256 + [int]$bytes[7]
                if ($major -gt 55) {
                    $bytes[6] = 0  # major high byte
                    $bytes[7] = 55 # major low byte (Java 11)
                    [System.IO.File]::WriteAllBytes($_.FullName, $bytes)
                    $patched++
                }
            }
        }
    }
    
    Write-Host "Patched $patched .class files from Java 17 to Java 11"
    
    # Remove output if exists
    if (Test-Path $OutputJar) { Remove-Item $OutputJar -Force }
    
    # Repack jar
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $OutputJar)
    
    Write-Host "Output: $OutputJar ($((Get-Item $OutputJar).Length / 1MB) MB)"
} finally {
    Remove-Item $tempDir -Recurse -Force
}
