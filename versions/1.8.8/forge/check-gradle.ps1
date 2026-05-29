$ErrorActionPreference = 'SilentlyContinue'
cd "c:\Users\xjh37\Desktop\MSF\msf-friends-multi\versions\1.8.8\forge"
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-17"

# Test the file() resolution in Gradle
$testScript = @"
task printJars {
    doLast {
        configurations.compileClasspath.files.each { f ->
            println "JAR: ${f} (exists: ${f.exists()})"
        }
    }
}
"@

# Write a quick test gradle script
Set-Content -Path "test-paths.gradle" -Value $testScript
$output = & .\gradlew.bat -b build.gradle printJars --no-daemon 2>&1
$output | Select-String "JAR:" | Select-Object -First 20
