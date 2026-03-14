package net.badgersmc.nexus.paper.commands

import net.badgersmc.nexus.commands.CommandParameter
import net.badgersmc.nexus.commands.annotations.Command
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/** Full metadata for a root command discovered via classpath scanning. */
data class PaperCommandDefinition(
    val commandClass: KClass<*>,
    val annotation: Command,
    val subcommands: List<PaperSubcommandDefinition>
)

/** Metadata for a single @Subcommand method. */
data class PaperSubcommandDefinition(
    /** Path segments — e.g., ["admin", "setup"] for @Subcommand("admin setup") */
    val path: List<String>,
    val method: KFunction<*>,
    val parameters: List<CommandParameter>,
    val permission: String?,
    val isPlayerOnly: Boolean,
    val isAsync: Boolean
)
