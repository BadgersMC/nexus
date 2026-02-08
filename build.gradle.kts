plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

group = "net.badgersmc"
version = "1.4.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Kotlin Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Classpath Scanning
    implementation("io.github.classgraph:classgraph:4.8.174")

    // Configuration (YAML)
    implementation("com.charleskorn.kaml:kaml:0.59.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
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
            artifactId = "nexus"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Nexus")
                description.set("A lightweight dependency injection and configuration framework for Hytale modding")
                url.set("https://github.com/BadgersMC/nexus")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("badgersmc")
                        name.set("BadgersMC Team")
                        organization.set("BadgersMC")
                        organizationUrl.set("https://github.com/BadgersMC")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/BadgersMC/nexus.git")
                    developerConnection.set("scm:git:ssh://github.com/BadgersMC/nexus.git")
                    url.set("https://github.com/BadgersMC/nexus")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/BadgersMC/nexus")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
