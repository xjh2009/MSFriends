// Init script to add 1.17.1:forge project when it's missing from settings
gradle.beforeSettings {
    if (!findProject(":versions:1.17.1:forge") != null) {
        include(":versions:1.17.1:forge")
    }
}
