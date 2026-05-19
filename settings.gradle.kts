pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "nexus"

include("nexus-core")
include("nexus-paper")
// nexus (root) becomes the nexus-hytale aggregator — leave existing code there
