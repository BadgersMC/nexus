plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    mavenLocal()
}

dependencies {
    api(project(":nexus-core"))
    api(project(":nexus-i18n"))
    api(project(":nexus-paper-gui"))

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")
    testImplementation("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "net.badgersmc"
            artifactId = "nexus-paper-bedrock"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}
