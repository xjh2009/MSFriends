// Gradle init script to include the 1.19.2 modules
// Used when settings.gradle.kts is locked by VS Code
gradle.beforeSettings {
    include(":common")
    include(":versions:1.19.2:common")
    include(":versions:1.19.2:forge")
}
