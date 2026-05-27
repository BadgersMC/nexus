plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal() // opt-in via -PuseMavenLocal=true — never on CI
    }
}

dependencies {
    api(project(":nexus-core"))
    api("com.zaxxer:HikariCP:5.1.0")

    // JDBC drivers are compileOnly — consumer plugins ship whichever they need
    // via Paper's runtime library loader (or shadow them explicitly).
    compileOnly("org.xerial:sqlite-jdbc:3.46.1.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    compileOnly("org.postgresql:postgresql:42.7.4")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
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
            artifactId = "nexus-persistence"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}
