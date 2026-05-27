package net.badgersmc.nexus.permissions.gradle

import net.badgersmc.nexus.permissions.PaperPluginYmlMerger
import net.badgersmc.nexus.permissions.PermissionTree
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Reads the consumer's permission tree from [tree] and merges it into
 * the `paper-plugin.yml` already staged under `build/resources/main/`
 * by `processResources`. Re-writes the file in place. See REQ-203.
 *
 * Up-to-date checks are intentionally disabled (everything marked
 * `@Internal`) because the input file is also the output file and the
 * PermissionTree is not Serializable. Cheap to re-run.
 */
abstract class GenerateNexusPermissionsTask : DefaultTask() {

    @get:Internal
    abstract val tree: Property<PermissionTree>

    @get:Internal
    abstract val pluginYml: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = pluginYml.get().asFile
        if (!file.exists()) {
            // Fail loudly. A silent skip ships a jar with no permissions
            // block, which is exactly the regression this plugin exists
            // to prevent.
            throw GradleException(
                "nexus-permissions: expected staged paper-plugin.yml at $file, " +
                    "but it does not exist. Ensure src/main/resources/paper-plugin.yml " +
                    "is present so processResources can stage it before this task runs."
            )
        }
        val merged = PaperPluginYmlMerger.merge(file.readText(), tree.get())
        file.writeText(merged)
    }
}
