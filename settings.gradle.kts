pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "nexus"

include("nexus-core")
include("nexus-paper")
include("nexus-resources")
include("nexus-i18n")
include("nexus-persistence")
include("nexus-scheduler")
include("nexus-paper-loader")
include("nexus-paper-gui")
include("nexus-paper-bedrock")
include("nexus-paper-listeners")
include("nexus-vault")
include("nexus-papi")
// Root project is a pure aggregator — no source, no publishable artifact.
