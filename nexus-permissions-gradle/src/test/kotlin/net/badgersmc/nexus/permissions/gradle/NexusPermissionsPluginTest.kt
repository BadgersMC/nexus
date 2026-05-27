package net.badgersmc.nexus.permissions.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Red test for TDD-203 — drives the Gradle plugin via TestKit against a
 * synthetic consumer project. Asserts that after `classes` runs, the
 * built paper-plugin.yml in `build/resources/main/` has had its
 * permissions block populated from the consumer's DSL (REQ-203).
 */
class NexusPermissionsPluginTest {

    @TempDir lateinit var projectDir: Path

    @Test
    fun `classes task produces paper-plugin yml with merged permissions block`() {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText("""rootProject.name = "consumer"""")

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            import net.badgersmc.nexus.permissions.Default

            plugins {
                java
                id("net.badgersmc.nexus.permissions")
            }

            nexusPermissions {
                tree {
                    node("foo.admin", default = Default.OP, description = "Admin root") {
                        child("reload")
                        child("import", default = Default.NOT_OP)
                    }
                }
            }
            """.trimIndent()
        )

        val resources = projectDir.resolve("src/main/resources").toFile()
        resources.mkdirs()
        File(resources, "paper-plugin.yml").writeText(
            """
            name: ConsumerPlugin
            main: com.example.Consumer
            api-version: '1.21'

            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("classes", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Gradle build did not succeed:\n${result.output}",
        )

        val generated = projectDir.resolve("build/resources/main/paper-plugin.yml").toFile().readText()

        assertTrue("permissions:" in generated, "Expected permissions: block. Got:\n$generated")
        assertTrue("foo.admin:" in generated, "Expected foo.admin entry. Got:\n$generated")
        assertTrue("foo.admin.reload:" in generated, "Expected foo.admin.reload entry. Got:\n$generated")
        assertTrue("foo.admin.import:" in generated, "Expected foo.admin.import entry. Got:\n$generated")
        assertTrue("default: not op" in generated, "Expected NOT_OP mapping. Got:\n$generated")
        assertTrue("name: ConsumerPlugin" in generated, "Consumer's name: must survive. Got:\n$generated")
        assertTrue("main: com.example.Consumer" in generated, "Consumer's main: must survive. Got:\n$generated")
    }

    /**
     * Regression: pluginYml used to be hardcoded to
     * `build/resources/main/paper-plugin.yml`. Consumers that customise
     * `processResources.destinationDir` would silently miss the merge.
     */
    @Test
    fun `merge target follows processResources destinationDirectory when consumer customises it`() {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText("""rootProject.name = "consumer"""")

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            import net.badgersmc.nexus.permissions.Default

            plugins {
                java
                id("net.badgersmc.nexus.permissions")
            }

            // Java plugin's processResources.destinationDir is driven
            // by sourceSets.main.output.resourcesDir — relocate it the
            // canonical way and processResources will follow.
            sourceSets {
                named("main") {
                    output.resourcesDir = layout.buildDirectory.dir("custom-resources").get().asFile
                }
            }

            nexusPermissions {
                tree {
                    node("foo.admin", default = Default.OP) {
                        child("reload")
                    }
                }
            }
            """.trimIndent()
        )

        val resources = projectDir.resolve("src/main/resources").toFile()
        resources.mkdirs()
        File(resources, "paper-plugin.yml").writeText(
            """
            name: ConsumerPlugin
            main: com.example.Consumer
            api-version: '1.21'

            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("classes", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Gradle build did not succeed:\n${result.output}",
        )

        val customTarget = projectDir.resolve("build/custom-resources/paper-plugin.yml").toFile()
        assertTrue(customTarget.exists(), "Merge should land in the customised destinationDir")
        val text = customTarget.readText()
        assertTrue("foo.admin:" in text, "Custom-destination file must carry the merged tree")
    }

    /**
     * Regression: missing paper-plugin.yml used to be silently skipped,
     * shipping a jar without any permissions block. Now fails fast.
     */
    @Test
    fun `task fails the build when paper-plugin yml is missing`() {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText("""rootProject.name = "consumer"""")

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            import net.badgersmc.nexus.permissions.Default

            plugins {
                java
                id("net.badgersmc.nexus.permissions")
            }

            nexusPermissions {
                tree {
                    node("foo.admin", default = Default.OP) { child("reload") }
                }
            }
            """.trimIndent()
        )
        // Deliberately no src/main/resources/paper-plugin.yml.

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("classes", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            "nexus-permissions: expected staged paper-plugin.yml" in result.output,
            "Expected explicit failure message. Got:\n${result.output}",
        )
    }
}
