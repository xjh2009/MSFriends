// Init script to ensure forge 1.20.1 project is included
gradle.beforeSettings {
    include(":versions:1.20.1:forge")
}
