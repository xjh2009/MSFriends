// Init script to force-include Forge 1.20.1 project
gradle.beforeSettings {
    if (!findProject(":versions:1.20.1:forge") != null) {
        include(":versions:1.20.1:forge")
    }
}
